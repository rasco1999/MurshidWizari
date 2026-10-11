"""Device-free regression tests for the emulator's scroll-aware controls."""
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import anonymous_emulator_qa as ui


def tree(text, bounds):
    root = ET.Element('hierarchy')
    ET.SubElement(root, 'node', {'text': text, 'bounds': bounds})
    return root, b''


class RevealTests(unittest.TestCase):
    def test_visible_node_is_not_clicked_by_reveal(self):
        with patch.object(ui, 'ui_tree', return_value=tree('Inbox', '[0,100][200,150]')), \
             patch.object(ui, 'adb') as adb:
            self.assertEqual(ui.reveal('Inbox', 393, 852).attrib['text'], 'Inbox')
            adb.assert_not_called()

    def test_offscreen_node_is_scrolled_before_asserting(self):
        with patch.object(ui, 'ui_tree', side_effect=[
            tree('Inbox', '[0,1200][200,1260]'), tree('Inbox', '[0,120][200,180]')]), \
             patch.object(ui, 'adb') as adb, patch.object(ui.time, 'sleep'):
            ui.reveal('Inbox', 393, 852)
            self.assertEqual(adb.call_count, 1)
            self.assertEqual(adb.call_args.args[2], 'swipe')

    def test_tap_uses_revealed_bounds_once(self):
        with patch.object(ui, 'ui_tree', return_value=tree('Inbox', '[0,100][200,160]')), \
             patch.object(ui, 'adb') as adb:
            ui.tap_visible('Inbox', 393, 852)
            adb.assert_called_once_with('shell', 'input', 'tap', '100', '130')

    def test_missing_node_does_not_pass_silently(self):
        with patch.object(ui, 'ui_tree', return_value=tree('Other', '[0,100][200,160]')), \
             patch.object(ui, 'adb') as adb, patch.object(ui.time, 'sleep'):
            with self.assertRaises(AssertionError):
                ui.reveal('Inbox', 393, 852)
            self.assertEqual(adb.call_count, 5)


if __name__ == '__main__':
    unittest.main()
