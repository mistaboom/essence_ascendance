"""Audition overlapping tails honestly, including timing and clipping failure."""
import unittest
from unittest.mock import patch
import numpy as np
import quality


class OverlapTests(unittest.TestCase):
    def test_overlap_sums_full_tails_without_normalizing(self):
        with patch.object(quality, 'decode', return_value=np.full(6, .1)), \
                patch.object(quality, 'wav') as write:
            report = quality.overlapping_preview('source', 'destination', 3 / 48000, count=3)
        destination, actual, rate = write.call_args.args
        np.testing.assert_array_equal(actual, [.1] * 3 + [.2] * 6 + [.1] * 3)
        self.assertEqual((destination, rate), ('destination', 48000))
        self.assertEqual(report['onsetFrames'], [0, 3, 6])
        self.assertEqual(report['onsetSeconds'], [0, 3 / 48000, 6 / 48000])
        self.assertEqual((report['frameCount'], report['sourceFrames']), (12, 6))
        self.assertEqual((report['unscaledPeak'], report['count']), (.2, 3))
        self.assertEqual(report['intervalSeconds'], 3 / 48000)

    def test_fractional_frame_schedule_avoids_accumulated_drift(self):
        with patch.object(quality, 'decode', return_value=np.array([.25])), \
                patch.object(quality, 'wav') as write:
            report = quality.overlapping_preview('source', 'destination', 2.4 / 48000, count=4)
        self.assertEqual(report['onsetFrames'], [0, 2, 5, 7])
        np.testing.assert_array_equal(write.call_args.args[1], [.25, 0, .25, 0, 0, .25, 0, .25])
        self.assertEqual(report['frameCount'], 8)

    def test_invalid_timing_never_decodes_or_writes(self):
        invalid = [(0, 8), (-.1, 8), (float('nan'), 8), (float('inf'), 8),
                   (True, 8), ('0.4', 8), (1 / 96000, 8),
                   (.4, 1), (.4, 2.5), (.4, True), (.4, float('nan'))]
        for interval, count in invalid:
            with self.subTest(interval=interval, count=count), \
                    patch.object(quality, 'decode') as read, patch.object(quality, 'wav') as write:
                with self.assertRaises(ValueError):
                    quality.overlapping_preview('source', 'destination', interval, count)
                read.assert_not_called()
                write.assert_not_called()

    def test_clipping_overlap_fails_before_output(self):
        for amplitude in [.5, .6, -.6]:
            with self.subTest(amplitude=amplitude), \
                    patch.object(quality, 'decode', return_value=np.full(6, amplitude)), \
                    patch.object(quality, 'wav') as write:
                with self.assertRaisesRegex(ValueError, 'clips.*no output written'):
                    quality.overlapping_preview('source', 'destination', 3 / 48000, count=2)
                write.assert_not_called()

    def test_invalid_decoded_audio_never_writes(self):
        for audio in [np.array([]), np.array([np.nan]), np.array([np.inf]), np.zeros((2, 2))]:
            with self.subTest(audio=audio), patch.object(quality, 'decode', return_value=audio), \
                    patch.object(quality, 'wav') as write:
                with self.assertRaisesRegex(ValueError, 'finite, nonempty mono'):
                    quality.overlapping_preview('source', 'destination', .4)
                write.assert_not_called()


if __name__ == '__main__':
    unittest.main()
