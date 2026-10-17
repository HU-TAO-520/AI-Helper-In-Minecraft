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
    """把接口返回的一条整理成 {title, owner, bvid, link, short}。"""
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
    link = ('https://www.bilibili.com/video/' + bvid) if bvid else str(raw.get('short_link_v2') or '')
    short = str(raw.get('short_link_v2') or '').strip()
    if short.startswith('http://'):
        short = short[len('http://'):]
    elif short.startswith('https://'):
        short = short[len('https://'):]
    # 短链去掉协议头后给玩家看（服务器一般只拦 http(s):// 开头的整条链接，这样既不会被换成 [链接]，
    # 又能直接复制到浏览器打开）
    if not short and bvid:
        short = 'www.bilibili.com/video/' + bvid
    return {'title': title, 'owner': owner, 'bvid': bvid, 'link': link, 'short': short}


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
