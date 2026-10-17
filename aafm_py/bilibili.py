# -*- coding: utf-8 -*-
"""B 站「视频推荐」：抓首页/热门前 N 条（公开接口，不需要登录）。

两个接口互为兜底：
- ``x/web-interface/popular``  —— 热门（首页推荐流），带 ``short_link_v2``（b23.tv 短链）
- ``x/web-interface/ranking/v2`` —— 排行榜（热门挂了时用它）

⚠ B 站接口对 UA / Referer 比较敏感，必须带上；返回 ``code != 0`` 时当作失败。
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36')
BASE = 'https://api.bilibili.com'


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


def _item(raw: Dict[str, Any]) -> Optional[Dict[str, str]]:
    """把接口返回的一条整理成 {title, owner, bvid, link}。

    ``link`` 保留完整协议头（``https://b23.tv/…``）—— 客户端会把 http(s) 链接渲染成可点击的，
    点一下就打开浏览器（有些服务器会把它显示成可点的「[链接]」，同样能点开）。
    """
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
    return {'title': title, 'owner': owner, 'bvid': bvid, 'link': short}


def popular(limit: int = 5) -> List[Dict[str, str]]:
    """首页热门前 ``limit`` 条；失败返回空列表。"""
    limit = max(1, min(20, int(limit or 5)))
    data = _get_json(BASE + '/x/web-interface/popular?ps=%d&pn=1' % limit)
    items = ((data or {}).get('data') or {}).get('list') or []
    out = [x for x in (_item(i) for i in items[:limit]) if x]
    if out:
        return out
    # 兜底：排行榜
    data = _get_json(BASE + '/x/web-interface/ranking/v2?rid=0&type=all')
    items = ((data or {}).get('data') or {}).get('list') or []
    return [x for x in (_item(i) for i in items[:limit]) if x]
