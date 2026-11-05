"""Acoustic invariants for shared modal, pitch and noise primitives."""
import copy
import tempfile
import unittest
from pathlib import Path
import numpy as np
import synthesis
import quality
from recipes import resolve
from storage import REPO, read


def recipe(layer, duration=1):
    return {'schema':1, 'assetId':'test', 'seed':7, 'sampleRate':48000,
            'duration':duration, 'peakDb':-6, 'layers':[dict(name='voice',gain=.1,
                envelope=[[0,1],[1,1]], **layer)]}


class PaletteTests(unittest.TestCase):
    def test_inharmonic_modal_partials_and_independent_decay(self):
        source=recipe({'type':'modal','frequency':200,'partials':[
            {'ratio':1,'gain':1,'decaySeconds':.5},
            {'ratio':2.77,'gain':.5,'decaySeconds':.025}]})
        _,stems=synthesis.render(source); x=stems['voice']
        t=np.arange(len(x))/48000
        expected=.1*(np.sin(2*np.pi*200*t)*np.exp(-t/.5)
                     +.5*np.sin(2*np.pi*554*t)*np.exp(-t/.025))
        np.testing.assert_allclose(x,expected,atol=1e-13)

    def test_periodic_modal_grid_repeats_without_boundary_impulse(self):
        source=recipe({'type':'modal','frequency':261.63,'partials':[
            {'ratio':1,'gain':1,'phase':.2}, {'ratio':2.756,'gain':.2}],
            'fm':{'rate':.5,'depth':.05}},duration=2)
        source['loop']={'mode':'periodic'}
        _,stems=synthesis.render(source); x=stems['voice']
        self.assertLess(abs(x[0]-x[-1]),np.max(np.abs(np.diff(x)))*1.1)
        source['layers'][0]['partials'][1]['decaySeconds']=.1
        with self.assertRaises(ValueError): synthesis.render(source)

    def test_pitch_path_moves_up_and_down_without_phase_reset(self):
        source=recipe({'type':'tone','frequency':100,
                       'frequencyPoints':[[0,100],[.5,400],[1,100]]})
        _,stems=synthesis.render(source); x=stems['voice']
        def cycles(part): return np.count_nonzero(np.diff(np.signbit(part)))
        self.assertGreater(cycles(x[19200:28800]),cycles(x[:9600])*1.7)
        self.assertLess(np.max(np.abs(np.diff(x))),.006)
        broken=copy.deepcopy(source);broken['layers'][0]['frequencyPoints']=[[0,100],[.6,200],[.5,100],[1,200]]
        with self.assertRaises(ValueError): synthesis.render(broken)

    def test_noise_filter_morph_changes_spectrum_and_is_deterministic(self):
        source=recipe({'type':'noise','lowHz':150,'highHz':800,
                       'lowHzEnd':1600,'highHzEnd':4000,'spectralTiltDbOct':-3})
        _,stems=synthesis.render(source); _,again=synthesis.render(source)
        x=stems['voice'];np.testing.assert_array_equal(x,again['voice'])
        def centroid(part):
            p=np.abs(np.fft.rfft(part*np.hanning(len(part))))**2
            return np.sum(p*np.fft.rfftfreq(len(part),1/48000))/np.sum(p)
        self.assertGreater(centroid(x[-9600:]),centroid(x[:9600])*2)

    def test_new_batch_is_deterministic_with_editable_contained_layers(self):
        batch=read(REPO/'art/source/audio/batches/playful-chimes-20261101.json')
        self.assertEqual(len({item['asset'] for item in batch['items']}),48)
        for item in batch['items']:
            with self.subTest(asset=item['asset']):
                source,_=resolve(REPO/f'art/source/audio/recipes/{item["asset"]}.json')
                rate,stems=synthesis.render(source)
                _,again=synthesis.render(source)
                self.assertEqual(len(stems),len(source['layers']))
                for name,x in stems.items():
                    np.testing.assert_array_equal(x,again[name])
                    self.assertEqual(len(x),round(rate*source['duration']))
                    self.assertTrue(np.all(np.isfinite(x)))
                    self.assertLess(np.max(np.abs(x)),1)
                mix=sum(stems.values())
                self.assertGreater(np.sqrt(np.mean(mix**2)),.0001)
                self.assertLessEqual(np.max(np.abs(mix)),10**(source['peakDb']/20)+1e-12)
                if not source.get('loop'):
                    self.assertLess(max(abs(mix[0]),abs(mix[-1])),1e-10)

    def test_beam_note_counts_dropouts_and_loudness_do_not_grow_with_density(self):
        for destination in ('player','infuser'):
            previous=[];levels=[]
            for count in range(9):
                source,_=resolve(REPO/f'art/source/audio/recipes/beam_{destination}_p{count}_v2.json')
                self.assertEqual(len(source['layers']),count+1)
                self.assertEqual(source['design']['noteCount'],count+1)
                pitches=[layer['frequency'] for layer in source['layers']]
                self.assertEqual(pitches[:count],previous)
                previous=pitches
                self.assertTrue(all(point[1]>0 for point in source['layers'][0]['envelope']))
                for upper in source['layers'][1:]:
                    self.assertTrue(any(point[1]==0 for point in upper['envelope']))
                _,stems=synthesis.render(source)
                levels.append(20*np.log10(np.sqrt(np.mean(sum(stems.values())**2))))
            self.assertLess(max(levels)-min(levels),3)

    def test_loop_audition_fades_only_outer_edges_and_preserves_raw_joins(self):
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/'source.wav';dest=Path(td)/'audition.wav'
            t=np.arange(48000)/48000
            synthesis.wav(path,.1*np.sin(2*np.pi*240*t+.4),48000)
            original=quality.decode(path)
            quality.loop_audition(path,dest,periods=3,fade_seconds=.2)
            actual=quality.decode(dest);expected=np.tile(original,3)
            self.assertEqual(len(actual),len(expected))
            self.assertEqual(actual[0],0);self.assertEqual(actual[-1],0)
            np.testing.assert_allclose(actual[9600:-9600],expected[9600:-9600],atol=2e-7,rtol=0)
            for joint in (48000,96000):
                np.testing.assert_allclose(actual[joint-480:joint+480],expected[joint-480:joint+480],atol=2e-7,rtol=0)


if __name__=='__main__': unittest.main()
