# -*- coding: utf-8 -*-
"""B 站「视频推荐」：抓一批视频给玩家（公开接口，不需要登录）。

三种来源（``kind``）：

- ``anime``（默认）—— 番剧**追番榜**（``pgc/web/rank/list?season_type=1``，更新多、适合追番）
- ``hot``         —— B 站**首页热门**（``x/web-interface/popular``）
- ``rank``        —— B 站**全站排行**（``x/web-interface/ranking/v2?rid=0``）

每次都会抓一个**大池子**（几十条），再从中随机挑几条，并能排除最近推过的（``exclude``）——
这样「冷却过后再点」就能**换一批新的**，不会每次都推同 5 条。

⚠ B 站接口对 UA / Referer 敏感，必须带；返回 ``code != 0`` 视为失败。
"""
from __future__ import annotations

import random
from typing import Any, Dict, Iterable, List, Optional

UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36')
BASE = 'https://api.bilibili.com'

SOURCES = ('anime', 'hot', 'rank')


def _headers() -> Dict[str, str]:
    return {
        'User-Agent': UA,
        'Referer': 'https://www.bilibili.com/',
        'Accept': 'application/json, text/plain, */*',
    }


def _get_json(url: str, timeout: float = 10.0) -> Optional[Dict[str, Any]]:
    try:
        import httpx
        r = httpx.get(url, headers=_headers(), timeout=timeout, follow_redirects=True)
        if r.status_code != 200:
            return None
        data = r.json()
        if not isinstance(data, dict) or data.get('code') != 0:
            return None
        return data
    except Exception:  # noqa: BLE001
        return None


# ----------------------------------------------------------------------
# 各来源：把接口返回整理成统一的 {id, title, owner, link}
# ----------------------------------------------------------------------
def _from_video(raw: Dict[str, Any]) -> Optional[Dict[str, str]]:
    """普通投稿（热门 / 排行）→ 统一格式。"""
    if not isinstance(raw, dict):
        return None
    bvid = str(raw.get('bvid') or '').strip()
    title = str(raw.get('title') or '').strip()
    if not title:
        return None
    owner = ''
    o = raw.get('owner')
    if isinstance(o, dict):
        owner = str(o.get('name') or '').strip()
    short = str(raw.get('short_link_v2') or '').strip()
    if not short.startswith('http'):
        short = ('https://www.bilibili.com/video/' + bvid) if bvid else ''
    if not short:
        return None
    return {'id': bvid or short, 'title': title,
            'owner': f'（{owner}）' if owner else '', 'link': short}


def _from_bangumi(raw: Dict[str, Any]) -> Optional[Dict[str, str]]:
    """番剧（追番榜）→ 统一格式；备注里带上「更新至…」或评分。"""
    if not isinstance(raw, dict):
        return None
    title = str(raw.get('title') or '').strip()
    if not title:
        return None
    season_id = raw.get('season_id') or 0
    url = str(raw.get('url') or '').strip()
    if not url and season_id:
        url = 'https://www.bilibili.com/bangumi/play/ss%d' % int(season_id)
    if not url:
        return None
    note = ''
    ne = raw.get('new_ep')
    if isinstance(ne, dict):
        note = str(ne.get('index_show') or '').strip()
    if not note:
        note = str(raw.get('rating') or '').strip()
    return {'id': 'ss%d' % int(season_id) if season_id else url,
            'title': title, 'owner': f'（{note}）' if note else '', 'link': url}


def _pool(kind: str, size: int) -> List[Dict[str, str]]:
    """抓一个大池子（尽量多），失败返回空列表。"""
    out: List[Dict[str, str]] = []
    if kind == 'anime':
        # 追番榜：一台 99 条，先日榜再三日榜
        for day in (3, 1):
            data = _get_json(BASE + '/pgc/web/rank/list?season_type=1&day=%d' % day)
            lst = ((data or {}).get('result') or {}).get('list') or []
            out = [x for x in (_from_bangumi(i) for i in lst[:size]) if x]
            if out:
                break
    elif kind == 'hot':
        # 首页热门：ps 最大 20，翻几页凑池子
        for pn in (1, 2, 3):
            data = _get_json(BASE + '/x/web-interface/popular?ps=20&pn=%d' % pn)
            lst = ((data or {}).get('data') or {}).get('list') or []
            out.extend(x for x in (_from_video(i) for i in lst) if x)
            if len(out) >= size:
                break
    else:  # rank：全站排行一次给 100 条
        data = _get_json(BASE + '/x/web-interface/ranking/v2?rid=0&type=all')
        lst = ((data or {}).get('data') or {}).get('list') or []
        out = [x for x in (_from_video(i) for i in lst[:size]) if x]
    return out[:size] if size else out


def recommend(kind: str = 'anime', limit: int = 5,
              exclude: Optional[Iterable[str]] = None,
              size: int = 30) -> List[Dict[str, str]]:
    """挑 ``limit`` 条推荐。

    ``exclude`` 里是最近推过的 id —— 会尽量避开（都推过一遍就允许重复）。
    """
    kind = str(kind or 'anime').strip().lower()
    if kind not in SOURCES:
        kind = 'anime'
    limit = max(1, min(10, int(limit or 5)))
    size = max(limit, min(100, int(size or 30)))
    pool = _pool(kind, size)
    if not pool:
        return []
    used = {str(x) for x in (exclude or ())}
    fresh = [x for x in pool if str(x.get('id')) not in used]
    if len(fresh) < limit:
        fresh = pool  # 池子里的都推过一遍了，允许重复
    random.shuffle(fresh)
    return fresh[:limit]


def popular(limit: int = 5) -> List[Dict[str, str]]:
    """兼容旧调用：首页热门前 ``limit`` 条。"""
    return recommend('hot', limit)
