"""Listening-room accessibility, ownership, grouping and content safety checks."""
import unittest
from unittest.mock import patch
from contextlib import redirect_stdout
from io import StringIO
from html.parser import HTMLParser
from player import page


class Document(HTMLParser):
    def __init__(self,text):
        super().__init__()
        self.elements=[]
        self.text=[]
        self.feed(text)
    def handle_starttag(self,tag,attrs):
        self.elements.append((tag,dict(attrs)))
    def handle_data(self,data):
        self.text.append(data)


class PlayerTest(unittest.TestCase):
    def test_authoring_cap_skips_completed_and_resumes_without_reopening(self):
        import batch
        definition={'id':'test','items':[{'asset':f'asset{i}'} for i in range(12)]}
        manifest={'assets':{item['asset']:{'revisions':[]} for item in definition['items']}}
        manifest['assets']['asset0']['revisions']=[{'id':'r0001','preview':'previews/test/asset0.ogg'}]
        created=[]
        def author(args):
            created.append(args.asset)
            manifest['assets'][args.asset]['revisions'].append({'id':'r0001','preview':f'previews/test/{args.asset}.ogg'})
        with patch.object(batch,'read',return_value=manifest),patch.object(batch.audio,'author',side_effect=author),redirect_stdout(StringIO()):
            self.assertEqual(batch.author_chunk(definition,'unused'),8)
            self.assertEqual(created,[f'asset{i}' for i in range(1,9)])
            self.assertEqual(batch.author_chunk(definition,'unused'),3)
            self.assertEqual(created,[f'asset{i}' for i in range(1,12)])
            self.assertEqual(batch.author_chunk(definition,'unused'),0)
        self.assertEqual(len(created),len(set(created)))

    def test_partial_finalization_is_explicit_and_report_default_stays_strict(self):
        import batch
        definition={'id':'test','items':[{'asset':'done'},{'asset':'unfinished'}]}
        take={'id':'r0001','preview':'previews/test/done.ogg'}
        manifest={'assets':{'done':{'revisions':[take]},'unfinished':{'revisions':[]}}}
        with patch.object(batch,'read',return_value=manifest):
            self.assertEqual([r[0]['asset'] for r in batch.records(definition,None,available=True)],['done'])
            with self.assertRaisesRegex(ValueError,'No batch take: unfinished'):
                batch.records(definition,None)

    def test_every_file_has_accessible_native_playback_and_independent_review(self):
        cards=[{'id':'new','title':'New loop','src':'file:///C:/Audio/new.ogg',
                'repeatSrc':'file:///C:/Audio/new-repeat.wav','status':'pending','group':'chords'},
               {'id':'old','title':'Old candidate','src':'file:///C:/Audio/old.ogg','status':'rejected','group':'chords'},
               {'id':'native','title':'Native shield','src':'file:///C:/Audio/shield.ogg','status':'reference','group':'native'}]
        text=page('Review','Listen first',[{'id':'chords','title':'Chords','presentation':'steps'},
                  {'id':'native','title':'Minecraft'}],cards,'example')
        doc=Document(text)
        sounds=[attrs for tag,attrs in doc.elements if tag=='audio']
        self.assertEqual(len(sounds),4)
        self.assertTrue(all('controls' in sound and sound.get('aria-label') for sound in sounds))
        self.assertEqual({sound['src'] for sound in sounds},{card['src'] for card in cards}|{cards[0]['repeatSrc']})
        self.assertIn('Rejected take',text)
        self.assertIn('Awaiting your review',text)
        self.assertIn('Vanilla reference',text)
        self.assertIn('never approves or promotes',text)
        ids=[attrs['id'] for _,attrs in doc.elements if 'id' in attrs]
        self.assertEqual(len(ids),len(set(ids)))
        # Native controls remain usable even if local browser scripting is disabled.
        self.assertEqual(len([a for t,a in doc.elements if t=='button' and 'data-audio' in a]),3)

    def test_titles_notes_and_provenance_cannot_inject_markup(self):
        text=page('Title <script>bad()</script>','A "quote" & prose',[],[{
            'id':'safe','title':'<img src=x onerror=bad()>','src':'file:///C:/safe.ogg',
            'description':'<b>description</b>','notes':'</article><script>bad()</script>',
            'technical':{'provenance':'<iframe src="evil">'}}],'safe')
        doc=Document(text)
        self.assertFalse(any(tag in {'img','iframe','b'} for tag,_ in doc.elements))
        self.assertEqual(len([t for t,_ in doc.elements if t=='script']),1)
        self.assertIn('&lt;script&gt;',text)

    def test_duplicate_or_unsafe_identity_fails_before_render(self):
        card={'id':'a','title':'A','src':'file:///C:/a.ogg'}
        with self.assertRaises(ValueError):
            page('T','',[],[card,card],'batch')
        with self.assertRaises(ValueError):
            page('T','',[],[{**card,'id':'bad"id'}],'batch')


if __name__=='__main__':
    unittest.main()
