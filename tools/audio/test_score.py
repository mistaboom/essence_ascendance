"""Shared instrument score behavior and the actual animation-length compositions."""
import copy
import unittest
from pathlib import Path
import numpy as np
import synthesis
from recipes import resolve


class ScoreTests(unittest.TestCase):
    def test_events_share_instrument_stem_and_final_only_normalization(self):
        voice={'name':'voice','type':'tone','frequency':300,'gain':.8,
               'harmonics':[1,.1],'envelope':[[0,0],[.02,1],[.8,.7],[1,0]]}
        events=[{'startSeconds':.1,'lengthSeconds':.3,'frequency':300},
                {'startSeconds':.2,'lengthSeconds':.3,'frequency':450}]
        r={'schema':1,'assetId':'score_test','seed':8,'sampleRate':48000,
           'duration':.6,'peakDb':-9,'layers':[{**voice,'events':events}]}
        rate,stems=synthesis.render(r)
        self.assertEqual(list(stems),['voice'])
        expanded={**r,'layers':[{**voice,**e,'name':f'note_{i}'} for i,e in enumerate(events)]}
        _,parts=synthesis.render(expanded)
        np.testing.assert_allclose(stems['voice'],sum(parts.values()),rtol=1e-13,atol=1e-15)
        self.assertEqual(rate,48000)
        self.assertAlmostEqual(float(np.max(np.abs(stems['voice']))),10**(-9/20),places=12)
        self.assertTrue(np.all(stems['voice'][:4800]==0))

    def test_score_containment_nesting_and_loops_rejected(self):
        base={'schema':1,'assetId':'score_test','seed':8,'sampleRate':48000,
            'duration':1,'layers':[{'name':'voice','type':'tone','frequency':300,'gain':.1,
            'envelope':[[0,0],[.1,1],[1,0]],'events':[{'startSeconds':.8,'lengthSeconds':.3}]}]}
        with self.assertRaises(ValueError): synthesis.render(base)
        base['layers'][0]['events'][0]['lengthSeconds']=.2
        nested=copy.deepcopy(base);nested['layers'][0]['events'][0]['events']=[]
        with self.assertRaises(ValueError): synthesis.render(nested)
        loop=copy.deepcopy(base);loop['loop']={'mode':'periodic'}
        with self.assertRaises(ValueError): synthesis.render(loop)
        _,a=synthesis.render(base);_,b=synthesis.render(base)
        self.assertTrue(np.array_equal(a['voice'],b['voice']))

    def test_ceremonies_match_real_animation_duration_and_crown(self):
        root=Path(__file__).resolve().parents[2]/'art/source/audio/recipes'
        for asset,duration,crown in [('ascendance_ceremony_v3',8,4.64),('awakening_ceremony_v3',3.8,2.204)]:
            r,_=resolve(root/(asset+'.json'))
            rate,stems=synthesis.render(r)
            self.assertEqual(len(stems),12)
            mix=sum(stems.values())
            self.assertEqual(len(mix),round(duration*rate))
            self.assertEqual(mix[0],0)
            self.assertEqual(mix[-1],0)
            release=stems['10_crown_release']
            self.assertTrue(np.all(release[:round(crown*rate)]==0))
            self.assertGreater(float(np.max(np.abs(release[round(crown*rate):]))),.02)
            self.assertLessEqual(float(np.max(np.abs(mix))),10**(-5.5/20)+1e-12)


if __name__=='__main__': unittest.main()
