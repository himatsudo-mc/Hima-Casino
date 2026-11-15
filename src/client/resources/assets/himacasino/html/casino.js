/* HimaCasino — Chromium GUI 共通ライブラリ
 *
 * Java → JS : window.casino.setState(state)  (MCEF の executeJavaScript から呼ばれる)
 * JS → Java : Casino.send({a: "hit"})        (cefQuery 経由。開発用サーバーでは fetch にフォールバック)
 */
(function () {
  'use strict';
  const Casino = (window.Casino = {});
  let handler = null;
  let last = null;
  let lastJson = null;

  window.casino = {
    setState(s) {
      last = s;
      // 開発用サーバー: サーバー側が GUI を閉じた (回転演出中など) ことを表示する
      let ov = document.getElementById('server-closed');
      if (s && s._ui === 'closed') {
        if (!ov) {
          ov = document.createElement('div');
          ov.id = 'server-closed';
          ov.style.cssText = 'position:fixed;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,.8);color:#f2c14e;font:800 28px sans-serif;z-index:9';
          ov.textContent = '（サーバーが画面を閉じました — ワールド内で演出中）';
          document.body.appendChild(ov);
        }
      } else if (ov) { ov.remove(); }
      const json = JSON.stringify(s);
      if (json === lastJson) return; // 変化がなければ再描画しない
      lastJson = json;
      if (handler) handler(s);
    }
  };

  Casino.onState = function (fn) {
    handler = fn;
    if (last) fn(last);
  };

  Casino.send = function (obj) {
    const body = JSON.stringify(obj);
    if (typeof window.cefQuery === 'function') {
      window.cefQuery({ request: body, persistent: false, onSuccess() {}, onFailure() {} });
    } else {
      // 開発用 (DevServer): 実際の Java ロジックに HTTP でつなぐ
      fetch('/api/action', { method: 'POST', body }).then(r => r.json()).then(s => window.casino.setState(s)).catch(() => {});
    }
  };

  // ── ステージの拡大縮小 ──────────────────────────────────────────────
  function fit() {
    const st = document.querySelector('.stage');
    if (!st) return;
    const k = Math.min(window.innerWidth / 1040, window.innerHeight / 680);
    st.style.transform = 'translate(-50%, -50%) scale(' + k + ')';
  }
  window.addEventListener('resize', fit);

  // ── DOM helper ───────────────────────────────────────────────────────
  Casino.el = function (tag, cls, html) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html != null) e.innerHTML = html;
    return e;
  };

  Casino.fmt = n => Math.round(n).toLocaleString('en-US');

  Casino.balanceText = function (s) {
    return s.economy
      ? '残高 <b>' + Casino.fmt(s.balance) + '</b> ' + s.symbol
      : '無料モード';
  };

  // 手札の描画: 新しく配られたカードだけアニメーションさせる
  const counts = {};
  Casino.dealCards = function (key, cards, make) {
    const prev = counts[key] || 0;
    const start = cards.length < prev ? 0 : prev; // 新しいラウンドなら最初から
    counts[key] = cards.length;
    return cards.map(function (c, i) {
      const e = make(c, i);
      if (i < start) e.style.animation = 'none';
      else e.style.animationDelay = ((i - start) * 90) + 'ms';
      return e;
    });
  };

  Casino.card = function (c, opts) {
    opts = opts || {};
    const d = Casino.el('div', 'card');
    if (!c || c.hidden) {
      d.classList.add('back');
    } else {
      if (c.red) d.classList.add('red');
      d.innerHTML = '<span class="tl">' + c.r + '<br>' + c.s + '</span><span class="r">' + c.r + '</span><span class="s">' + c.s + '</span><span class="br">' + c.r + '<br>' + c.s + '</span>';
    }
    if (opts.delay) d.style.animationDelay = opts.delay + 'ms';
    return d;
  };

  // 数字だけのカード (HIGH & LOW 用)
  Casino.rankCard = function (rank, opts) {
    opts = opts || {};
    const d = Casino.el('div', 'card' + (opts.red ? ' red' : ''));
    d.innerHTML = '<span class="r" style="font-size:54px">' + rank + '</span>';
    return d;
  };

  // チップ列: ボタンを押すと {a:"chip", v} を送る
  Casino.chips = function (s, opts) {
    opts = opts || {};
    const row = Casino.el('div', 'chips');
    (s.chips || []).forEach(v => {
      const c = Casino.el('div', 'chip' + (opts.selected === v ? ' sel' : ''), v >= 1000 ? (v / 1000) + 'k' : v);
      c.dataset.v = v;
      c.onclick = () => Casino.send({ a: opts.action || 'chip', v: v });
      row.appendChild(c);
    });
    return row;
  };

  Casino.btn = function (label, cls, onclick, disabled) {
    const b = Casino.el('button', 'btn ' + (cls || ''), label);
    b.disabled = !!disabled;
    b.onclick = onclick;
    return b;
  };

  Casino.frame = function (title, sub) {
    document.body.innerHTML =
      '<div class="stage"><div class="topbar"><div class="title">' + title + '<small>' + (sub || '') + '</small></div>' +
      '<div class="balance" id="balance"></div></div><div class="body" id="body"></div></div>';
    fit();
    return document.getElementById('body');
  };

  Casino.setBalance = function (s) {
    const b = document.getElementById('balance');
    if (b) b.innerHTML = Casino.balanceText(s);
  };

  window.addEventListener('DOMContentLoaded', function () {
    fit();
    // ページの準備ができたことを Java に伝える → 最新の状態が送られてくる
    Casino.send({ a: '__ready' });
    // 開発用サーバーでは状態をポーリングする (遅延精算などを反映)
    if (typeof window.cefQuery !== 'function') {
      setInterval(() => fetch('/api/state').then(r => r.json()).then(s => window.casino.setState(s)).catch(() => {}), 400);
    }
  });
})();
