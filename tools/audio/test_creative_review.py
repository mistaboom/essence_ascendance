"""Creative decisions never manufacture native mix or promotion acceptance."""
import copy
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import audio
from player import page


class CreativeReviewTests(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)/'repo'
        self.owner = self.repo/'art/audio-manifest.json'
        self.owner.parent.mkdir(parents=True)
        self.take = {'id':'r0001', 'review':{'status':'pending'},
                     'project':'project.aup3', 'backup':{'closed':True}, 'loop':True}
        self.owner.write_text(json.dumps({'assets':{'loop':{'scope':'world','revisions':[self.take]}}}))
        self.args = SimpleNamespace(asset='loop', revision='r0001',
            auxiliary=str(Path(self.temp.name)/'aux'),status='accepted',
            reviewer='User',at='2026-11-01 America/Anchorage',notes='The rest are great.')
        self.repo_patch = patch.object(audio,'REPO',self.repo)
        self.repo_patch.start()
        self.addCleanup(self.repo_patch.stop)
        self.validation = patch.object(audio,'verify_candidate')
        self.verify = self.validation.start()
        self.addCleanup(self.validation.stop)

    def current(self):
        return json.loads(self.owner.read_text())['assets']['loop']['revisions'][0]

    def test_creative_approval_preserves_final_gates_and_cannot_promote(self):
        audio.creative_review(self.args)
        record=self.current()
        self.assertEqual(record['review'], {'status':'pending'})
        self.assertEqual(record['creativeReview']['status'],'accepted')
        self.assertEqual(record['creativeReview']['notes'],'The rest are great.')
        self.assertFalse(any(key in record['creativeReview'] for key in
                             ('mixReviewed','loopReviewed','license','listened')))
        self.verify.assert_called_once()
        self.assertEqual(self.verify.call_args.args[2],'world')
        with self.assertRaisesRegex(ValueError,'Promotion requires an accepted take'):
            audio.promote(self.args)

    def test_exact_replay_is_idempotent_and_reconsideration_preserves_history(self):
        audio.creative_review(self.args)
        approved=copy.deepcopy(self.current()['creativeReview'])
        audio.creative_review(self.args)
        self.assertNotIn('creativeReviewHistory',self.current())
        self.args.status='revise_requested'
        self.args.notes='Please sharpen the attack.'
        audio.creative_review(self.args)
        record=self.current()
        self.assertEqual(record['creativeReviewHistory'],[approved])
        self.assertEqual(record['creativeReview']['status'],'revise_requested')
        self.assertEqual(record['review'],{'status':'pending'})

    def test_final_decision_and_blank_feedback_cannot_be_overwritten(self):
        self.args.notes=' '
        before=self.owner.read_bytes()
        with self.assertRaisesRegex(ValueError,'cannot be blank'):
            audio.creative_review(self.args)
        self.assertEqual(before,self.owner.read_bytes())
        self.args.notes='New opinion'
        manifest=json.loads(self.owner.read_text())
        manifest['assets']['loop']['revisions'][0]['review']={'status':'rejected'}
        self.owner.write_text(json.dumps(manifest))
        before=self.owner.read_bytes()
        with self.assertRaisesRegex(ValueError,'Final review is immutable'):
            audio.creative_review(self.args)
        self.assertEqual(before,self.owner.read_bytes())

    def test_player_distinguishes_sound_approval_revision_and_final_status(self):
        cards=[{'id':'approved','title':'Approved sound','src':'file:///approved.ogg',
                'status':'pending','creativeReview':{'status':'accepted','notes':'Good.'}},
               {'id':'revise','title':'Revise sound','src':'file:///revise.ogg',
                'status':'pending','creativeReview':{'status':'revise_requested','notes':'Sharper.'}},
               {'id':'new','title':'New take','src':'file:///new.ogg','status':'pending'},
               {'id':'final','title':'Rejected take','src':'file:///final.ogg','status':'rejected',
                'creativeReview':{'status':'accepted','notes':'Historical opinion.'}}]
        result=page('Review','',[],cards,'test')
        for text in ['Sound approved','Revision requested','Awaiting your review',
                     'Rejected take','In-game mix and final release review remain pending.',
                     '1 sound approvals','1 revisions requested']:
            self.assertIn(text,result)
        self.assertNotIn('Accepted take</span>',result)


if __name__=='__main__':
    unittest.main()
