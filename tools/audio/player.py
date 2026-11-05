"""Offline, accessible listening UI. The manifest remains the review owner."""
import html
import json
import re


def _escape(value):
    return html.escape(str(value), quote=True)


def _display_status(card):
    status = card.get('status', 'pending')
    creative = card.get('creativeReview', {}).get('status')
    if status == 'pending' and creative in {'accepted', 'revise_requested'}:
        return 'creative_' + creative
    return status


def _card(card):
    identifier = _escape(card['id'])
    status = _display_status(card)
    labels = {'pending': 'Awaiting your review', 'accepted': 'Accepted take',
              'rejected': 'Rejected take', 'reference': 'Vanilla reference',
              'demonstration': 'Listening demonstration',
              'creative_accepted': 'Sound approved',
              'creative_revise_requested': 'Revision requested'}
    label = labels.get(status, status)
    duration = card.get('duration')
    duration_text = f'{duration:.2f} s' if isinstance(duration, (int, float)) else ''
    meta = ' · '.join(str(v) for v in [card.get('tag'), duration_text, card.get('revision')] if v)
    title, description = _escape(card['title']), _escape(card.get('description', ''))
    output = [f'<article class="sound-card" id="sound-{identifier}" data-search="{title.lower()} {description.lower()}">',
        f'<div class="card-top"><span class="badge {_escape(status)}">{_escape(label)}</span><span class="meta">{_escape(meta)}</span></div>',
        f'<h3>{title}</h3><p class="description">{description}</p>',
        f'<div class="play-row"><button class="play-button" data-audio="audio-{identifier}" type="button" aria-label="Play {title}"><span aria-hidden="true">▶</span> Play</button>',
        f'<button class="reset-button" data-reset="audio-{identifier}" type="button" aria-label="Restart {title}">↺ Restart</button></div>',
        f'<audio id="audio-{identifier}" aria-label="{title}" controls preload="none" src="{_escape(card["src"])}"></audio>']
    if card.get('sequence'):
        output.append('<p class="sequence">'+_escape(card['sequence'])+'</p>')
    if card.get('repeatSrc'):
        output.extend(['<details class="repeat"><summary>Hear repeated playback</summary>',
            '<p>Same level; decoded audio is joined before playback. Loop checks use eight complete periods.</p>',
            f'<audio aria-label="Repeated {title}" controls preload="none" src="{_escape(card["repeatSrc"])}"></audio></details>'])
    links = card.get('links', [])
    if links:
        output.append('<div class="file-links">'+' · '.join(f'<a href="{_escape(url)}">{_escape(label)}</a>' for label, url in links)+'</div>')
    if card.get('notes'):
        output.append('<p class="review-notes">'+_escape(card['notes'])+'</p>')
    if status.startswith('creative_'):
        output.append('<p class="review-notes">'+_escape(card['creativeReview'].get('notes',''))+'</p>')
        output.append('<p class="review-notes">In-game mix and final release review remain pending.</p>')
    if card.get('technical'):
        output.append('<details class="technical"><summary>Measurements &amp; provenance</summary><p>These are technical records, not a judgment of how the sound feels.</p><pre>'+_escape(json.dumps(card['technical'], indent=2, ensure_ascii=False))+'</pre></details>')
    output.append('</article>')
    return ''.join(output)


def page(title, description, groups, cards, batch_id):
    """Render plain HTML with native audio fallback; never contacts external services."""
    seen = set()
    for card in cards:
        if not re.fullmatch('[A-Za-z0-9_-]+',card['id']):
            raise ValueError('Listening card ID must be simple')
        if card['id'] in seen:
            raise ValueError('Duplicate listening card ID')
        seen.add(card['id'])
    group_ids = [group['id'] for group in groups]
    if any(not re.fullmatch('[A-Za-z0-9_-]+',identifier) for identifier in group_ids):
        raise ValueError('Listening group ID must be simple')
    if len(set(group_ids)) != len(group_ids):
        raise ValueError('Duplicate listening group ID')
    rendered_groups = []
    for group in groups:
        members = [c for c in cards if c.get('group') == group['id']]
        if members:
            rendered_groups.append((group, members))
    other = [c for c in cards if c.get('group') not in group_ids]
    if other:
        identifier='other-sounds'
        while identifier in group_ids:
            identifier+='-more'
        rendered_groups.append(({'id': identifier, 'title': 'More sounds'}, other))
    statuses = {}
    for card in cards:
        status = _display_status(card)
        statuses[status] = statuses.get(status, 0) + 1
    counts = []
    for key, label in [('pending', 'to review'), ('accepted', 'accepted'), ('rejected', 'rejected'),
                       ('creative_accepted', 'sound approvals'), ('creative_revise_requested', 'revisions requested'),
                       ('reference', 'vanilla references'), ('demonstration', 'demonstrations')]:
        if statuses.get(key):
            counts.append(f'{statuses[key]} {label}')
    navigation = ''.join(f'<a href="#group-{_escape(g["id"])}">{_escape(g["title"])}</a>' for g, _ in rendered_groups)
    body = []
    for group, members in rendered_groups:
        presentation=' steps' if group.get('presentation')=='steps' else ''
        body.extend([f'<section class="sound-group{presentation}" id="group-{_escape(group["id"])}">',
                     f'<div class="group-heading"><h2>{_escape(group["title"])}</h2><span>{len(members)} tracks</span></div>',
                     f'<p class="group-description">{_escape(group.get("description", ""))}</p>',
                     '<div class="sound-grid">'+''.join(_card(c) for c in members)+'</div></section>'])
    return '''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>'''+_escape(title)+''' · Essence Ascendance</title>
<style>
:root{color-scheme:dark;--bg:#10191c;--panel:#1a272a;--line:#344447;--ink:#edf3ee;--muted:#a9bcb7;--accent:#cce8a8;--gold:#efcb94}
*{box-sizing:border-box}html{scroll-behavior:smooth;scroll-padding-top:95px}body{margin:0;background:radial-gradient(ellipse at 12% 0,#253735,transparent 55%),var(--bg);color:var(--ink);font:16px/1.55 system-ui,-apple-system,"Segoe UI",sans-serif}main{max-width:1280px;margin:auto;padding:52px 28px 65px}.eyebrow{font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:var(--accent);font-weight:700}h1{font-size:clamp(30px,5vw,53px);line-height:1.12;letter-spacing:-.035em;font-weight:650;margin:14px 0 17px;max-width:900px}.intro{max-width:820px;color:var(--muted);font-size:18px;margin:0 0 18px}.counts{color:var(--gold);font-size:14px}.notice{max-width:870px;color:var(--muted);font-size:13px;margin:13px 0 27px}.toolbar{position:sticky;top:0;z-index:4;display:flex;align-items:center;gap:12px;padding:14px 0;background:#10191cf2;backdrop-filter:blur(12px);border-block:1px solid var(--line)}input{width:min(420px,100%);padding:11px 14px;border:1px solid var(--line);border-radius:8px;background:#1a272a;color:var(--ink);font:inherit}input::placeholder{color:#91a6a0}.now-playing{color:var(--muted);font-size:13px;margin-left:auto;text-align:right}button{font:inherit;cursor:pointer;border-radius:8px;border:1px solid var(--line);padding:9px 14px;color:var(--ink);background:transparent}button:hover{filter:brightness(1.12);border-color:var(--accent)}button:focus-visible,a:focus-visible,input:focus-visible,summary:focus-visible{outline:3px solid var(--gold);outline-offset:3px}nav{display:flex;flex-wrap:wrap;gap:8px;margin:20px 0 36px}nav a{padding:6px 11px;border:1px solid var(--line);border-radius:20px;font-size:13px;color:var(--muted);text-decoration:none}nav a:hover{color:var(--ink);border-color:var(--accent)}.sound-group{margin:37px 0 48px}.group-heading{display:flex;align-items:baseline;gap:18px}.group-heading h2{font-size:26px;letter-spacing:-.02em;font-weight:600;margin:0}.group-heading>span{font-size:13px;color:var(--muted)}.group-description{color:var(--muted);margin:8px 0 20px;max-width:850px}.sound-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:17px}.sound-card{background:linear-gradient(145deg,#20303299,#192529);border:1px solid var(--line);border-radius:15px;padding:23px;min-width:0;transition:border-color .15s,background .15s}.sound-card.playing{border-color:var(--accent);background:#253b36}.card-top{display:flex;justify-content:space-between;align-items:center;gap:8px}.badge{font-size:11px;letter-spacing:.04em;border-radius:4px;padding:3px 7px;background:#364039;color:#dae8ce}.badge.reference{background:#303e4b;color:#cbddeb}.badge.rejected{background:#4c3134;color:#f0c4c3}.badge.accepted,.badge.creative_accepted{background:#284839;color:#c4ecd6}.badge.creative_revise_requested{background:#4c3d2c;color:#f0d3a8}.badge.demonstration{background:#433d31;color:#eed4a8}.meta{font-size:12px;color:var(--muted)}h3{font-size:20px;letter-spacing:-.015em;font-weight:620;margin:16px 0 8px}.description{font-size:14px;color:var(--muted);min-height:44px;margin:0 0 18px}.play-row{display:flex;align-items:center;gap:10px;margin-bottom:13px}.play-button{min-width:118px;background:var(--accent);color:#20301f;font-weight:650;border-color:transparent}.play-button span{display:inline-block;width:18px}.reset-button{font-size:13px;color:var(--muted)}audio{display:block;width:100%;height:36px}.sequence{color:var(--muted);font-size:12px;margin:12px 0}.file-links{font-size:12px;margin-top:16px}.file-links a,a{color:var(--accent)}details{font-size:12px;color:var(--muted);margin-top:17px;border-top:1px solid var(--line);padding-top:12px}summary{cursor:pointer;color:#c4d3ca}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:11px/1.55 ui-monospace,Consolas,monospace;background:#10191c;padding:12px;border-radius:6px;max-height:400px;overflow:auto}.review-notes{font-size:12px;color:var(--gold)}footer{border-top:1px solid var(--line);padding-top:20px;color:var(--muted);font-size:12px}.sound-card[hidden],.sound-group[hidden]{display:none}.empty{color:var(--muted);display:none}.empty.visible{display:block}@media(max-width:760px){main{padding:32px 16px}.sound-grid{grid-template-columns:1fr}.toolbar{flex-wrap:wrap}.toolbar input{flex:1;min-width:150px}.now-playing{width:100%;text-align:left;margin:0}.sound-card{padding:19px}.description{min-height:0}.card-top{align-items:flex-start}.meta{text-align:right}}
.steps .sound-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.steps .sound-card{padding:18px}.steps h3{font-size:18px}.steps .description{min-height:0;font-size:13px}.steps .card-top{flex-wrap:wrap}.steps .meta{text-align:left}.steps .play-button{min-width:98px}.steps .reset-button{padding-inline:8px}@media(max-width:1000px){.steps .sound-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:620px){.steps .sound-grid{grid-template-columns:1fr}}
</style></head><body><main>
<header><div class="eyebrow">Essence Ascendance · listening room</div><h1>'''+_escape(title)+'''</h1>
<p class="intro">'''+_escape(description)+'''</p><p class="counts">'''+_escape(' · '.join(counts))+'''</p>
<p class="notice">Play any sound below. Only one track plays at a time. Source levels are preserved; use a comfortable listening volume. Creative approval and in-game mix review remain separate from the technical measurements. Vanilla references are existing game audio, not newly authored candidates.</p></header>
<div class="toolbar"><label class="search-label" for="sound-search" hidden>Find a sound</label><input id="sound-search" type="search" placeholder="Find a sound…" aria-label="Find a sound"><button id="stop-all" type="button">■ Stop all</button><div class="now-playing" id="now-playing" aria-live="polite">Ready to listen</div></div>
<nav aria-label="Sound groups">'''+navigation+'''</nav><p class="empty" id="empty">No matching sounds. Try another word.</p>
'''+''.join(body)+'''
<footer>Batch: '''+_escape(batch_id)+''' · Review status is a snapshot of art/audio-manifest.json when this page was generated. Regenerate the report after recording a decision. This page never approves or promotes audio.</footer>
<script>
(() => {
const tracks = [...document.querySelectorAll('audio')];
const indicator = document.getElementById('now-playing');
function update(track) {
 const card = track.closest('.sound-card');
 const playing = [...card.querySelectorAll('audio')].some(a => !a.paused && !a.ended);
 card.classList.toggle('playing', playing);
 const button = document.querySelector('[data-audio="'+track.id+'"]');
 if (button) { const active = !track.paused && !track.ended; button.innerHTML = active ? '<span aria-hidden="true">Ⅱ</span> Pause' : '<span aria-hidden="true">▶</span> Play'; button.setAttribute('aria-label', (active?'Pause ':'Play ')+card.querySelector('h3').textContent); }
}
tracks.forEach(track => {
 track.addEventListener('play', () => { tracks.forEach(other => { if(other !== track) other.pause(); }); indicator.textContent = 'Playing · '+track.getAttribute('aria-label'); update(track); });
 ['pause','ended'].forEach(event => track.addEventListener(event, () => { update(track); if(tracks.every(a=>a.paused || a.ended)) indicator.textContent = 'Ready to listen'; }));
 track.addEventListener('error', () => { indicator.textContent = 'Could not open this file. Check the local audio path or use the file link.'; update(track); });
});
function play(track) { track.play().catch(() => {indicator.textContent = 'Playback could not start. Try the native audio controls below.';}); }
document.querySelectorAll('[data-audio]').forEach(button => button.addEventListener('click', () => { const track = document.getElementById(button.dataset.audio); if(track.paused) play(track); else track.pause(); }));
document.querySelectorAll('[data-reset]').forEach(button => button.addEventListener('click', () => { const track = document.getElementById(button.dataset.reset); track.currentTime=0; play(track); }));
document.getElementById('stop-all').addEventListener('click', () => { tracks.forEach(track => {track.pause(); track.currentTime=0; update(track);}); indicator.textContent='Ready to listen'; });
document.getElementById('sound-search').addEventListener('input', event => {
 const query=event.target.value.trim().toLowerCase();
 document.querySelectorAll('.sound-card').forEach(card => {card.hidden=!card.dataset.search.includes(query);});
 document.querySelectorAll('.sound-group').forEach(group => {group.hidden=[...group.querySelectorAll('.sound-card')].every(card=>card.hidden);});
 document.getElementById('empty').classList.toggle('visible',[...document.querySelectorAll('.sound-card')].every(card=>card.hidden));
});
})();
</script></main></body></html>'''
