"""Meaningful authoring regression checks; no creative approval."""
import copy
import tempfile
import unittest
from pathlib import Path
import numpy as np
import encoding
import quality
import synthesis
from recipes import resolve
from storage import REPO, read, write


class ProductionTests(unittest.TestCase):
    def test_presets_freeze_independent_of_library_and_reject_recursion(self):
        with tempfile.TemporaryDirectory() as root:
            root=Path(root)
            library={'schema':1,'presets':{'test':{'type':'tone','frequency':100}}}
            write(root/'library.json',library)
            write(root/'recipe.json',{'layers':[{'preset':'test','name':'tone','frequency':200}],
                                     'presetLibrary':'library.json'})
            recipe,inputs=resolve(root/'recipe.json',root)
            library['presets']['test']['frequency']=300
            write(root/'library.json',library)
            self.assertEqual(recipe['layers'][0]['frequency'],200)
            self.assertEqual(inputs['presets']['content']['presets']['test']['frequency'],100)
            library['presets']['test']['preset']='test'
            write(root/'library.json',library)
            with self.assertRaises(ValueError): resolve(root/'recipe.json',root)

    def test_every_batch_shape_has_editable_stems_and_deterministic_sum(self):
        batch=read(REPO/'art/source/audio/batches/warm-arcane-20261029.json')
        for item in batch['items']:
            recipe,_=resolve(REPO/f'art/source/audio/recipes/{item["asset"]}.json')
            rate,a=synthesis.render(recipe)
            _,b=synthesis.render(recipe)
            self.assertGreaterEqual(len(a),3)
            for name in a: np.testing.assert_array_equal(a[name],b[name])
            mix=sum(a.values())
            self.assertEqual(len(mix),round(recipe['duration']*rate))
            self.assertLess(np.max(np.abs(mix)),1)

    def test_periodic_decoded_seams_and_no_gap_over_eight_periods(self):
        with tempfile.TemporaryDirectory() as root:
            for name in ('machine_warm_a','channel_warm_a'):
                recipe,_=resolve(REPO/f'art/source/audio/recipes/{name}.json')
                rate,stems=synthesis.render(recipe)
                path=Path(root)/(name+'.wav'); ogg=path.with_suffix('.ogg')
                synthesis.wav(path,sum(stems.values()),rate)
                encoding.encode(path,ogg)
                encoding.validate(ogg,loop=True,expected_duration=4)
                q=quality.inspect(ogg,True)['decodedLoop']
                self.assertFalse(q['gapOrClickFlag'],q)
                self.assertEqual(q['totalFrames'],8*4*rate)
                broken=copy.deepcopy(recipe)
                broken['layers'][0]['frequencyEnd']=10
                with self.assertRaises(ValueError): synthesis.render(broken)

    def test_offset_layer_silence_and_sweep_stay_within_clip(self):
        recipe,_=resolve(REPO/'art/source/audio/recipes/denied_warm_a.json')
        rate,stems=synthesis.render(recipe)
        self.assertTrue(np.all(stems['02_lower_knock'][:round(.115*rate)]==0))
        recipe['layers'][1]['lengthSeconds']=1
        with self.assertRaises(ValueError): synthesis.render(recipe)

    def test_qc_catches_a_real_gap_and_does_not_hide_a_bright_attack(self):
        with tempfile.TemporaryDirectory() as root:
            rate=48000;t=np.arange(rate)/rate
            x=.1*np.sin(2*np.pi*192*t);x[:960]=0;x[-960:]=0
            path=Path(root)/'gap.wav';synthesis.wav(path,x,rate)
            self.assertTrue(quality.inspect(path,True)['decodedLoop']['gapOrClickFlag'])
            t=np.arange(4800)/rate
            x=.01*np.sin(2*np.pi*300*t)
            x[:480]+=.2*np.sin(2*np.pi*4000*t[:480])
            synthesis.wav(path,x,rate)
            self.assertGreater(quality.inspect(path)['energy2To5kFraction'],.9)


if __name__=='__main__': unittest.main()
