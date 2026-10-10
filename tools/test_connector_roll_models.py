"""Regression checks for connector model poses, UVs and chain geometry."""
import copy
import itertools
import json
import unittest

import generate_connector_roll_models as models

class ConnectorRollTests(unittest.TestCase):
    def test_four_quarter_turns_restore_every_basis(self):
        for axis in models.NORMALS.values():
            for direction in models.NORMALS.values():
                value = direction
                for _ in range(4):
                    value = models.turn(value, axis, 1)
                self.assertEqual(value, direction)
                self.assertEqual(models.turn(axis, axis, 1), axis)

    def test_all_variants_and_model_references(self):
        for name in models.NAMES:
            variants = json.loads((models.ASSETS / "blockstates" / (name + ".json"))
                                  .read_text(encoding="utf-8"))["variants"]
            for key, pose in variants.items():
                properties = dict(item.split("=") for item in key.split(","))
                self.assertIn(properties["roll"], ("0", "1", "2", "3"))
                base = key.rsplit(",roll=", 1)[0]
                for roll in range(4):
                    self.assertIn(base + ",roll=" + str(roll), variants)
                data = models.load_model(pose["model"])
                self.assertTrue(data["elements"])
                for element in data["elements"]:
                    self.assertTrue(all(a <= b for a, b in zip(element["from"], element["to"])))
                    for face in element["faces"].values():
                        self.assertIn(face.get("rotation", 0), (0, 90, 180, 270))
                        # Connector textures specify UVs; otherwise the model bounds
                        # would change the default UV mapping after a quarter turn.
                        self.assertIn("uv", face)

    def test_shaft_axis_matches_baked_geometry(self):
        for facing, forward in models.NORMALS.items():
            pose = {"north": {}, "south": {"y": 180}, "east": {"y": 90},
                    "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}[facing]
            base_side = (0, 0, 1) if facing in ("east", "west") else (1, 0, 0)
            for roll in range(4):
                shaft = models.transform((1, 0, 0), pose, forward, roll)
                expected = models.turn(base_side, forward, roll)
                self.assertEqual(tuple(abs(x) for x in shaft), tuple(abs(x) for x in expected))
                self.assertEqual(models.dot(shaft, forward), 0)

    def test_uv_and_bounds_restore_after_full_rotation(self):
        sample = {
            "textures": {"0": "vsfluidlink:block/connectors_common"},
            "elements": [{
                "from": [2, 3, 4], "to": [11, 13, 15],
                "faces": {name: {"texture": "#0", "uv": [1, 2, 7, 9], "rotation": 90}
                          for name in models.NORMALS},
            }],
        }
        for axis in models.NORMALS.values():
            value = copy.deepcopy(sample)
            for _ in range(4):
                value = models.rotate_model(value, {}, axis, 1)
            self.assertEqual(value, sample)

    def test_clicked_face_rotation_preserves_full_pose(self):
        def base_up(facing, inverted):
            value = models.NORMALS["south"] if facing == "up" else (
                models.NORMALS["north"] if facing == "down" else models.NORMALS["up"])
            return tuple(-x for x in value) if inverted else value

        def decode(facing, roll, inverted):
            axis = models.NORMALS[facing]
            return axis, models.turn(base_up(facing, inverted), axis, roll)

        def encode(forward, up, inverted):
            facing = next(name for name, value in models.NORMALS.items() if value == forward)
            choices = [roll for roll in range(4) if decode(facing, roll, inverted)[1] == up]
            self.assertEqual(len(choices), 1)
            return facing, choices[0]

        for facing in models.NORMALS:
            for roll in range(4):
                for inverted in (False, True):
                    for face in models.NORMALS.values():
                        current = facing, roll
                        initial = decode(*current, inverted)
                        for step in range(4):
                            forward, up = decode(*current, inverted)
                            expected = models.turn(forward, face, 1), models.turn(up, face, 1)
                            current = encode(*expected, inverted)
                            self.assertEqual(decode(*current, inverted), expected)
                            self.assertEqual(models.dot(*expected), 0)
                        self.assertEqual(decode(*current, inverted), initial)
                        self.assertEqual(current, (facing, roll))

    def test_chain_anchors_remain_in_shaft_plane(self):
        for forward in models.NORMALS.values():
            base = (0, 0, 1) if forward[0] else (1, 0, 0)
            for roll in range(4):
                shaft = models.turn(base, forward, roll)
                for connection in itertools.product((-1, 0, 1), repeat=3):
                    anchor = models.cross(shaft, connection)
                    if not any(anchor):
                        continue
                    self.assertEqual(models.dot(anchor, shaft), 0)
                    self.assertEqual(models.dot(anchor, connection), 0)
                    tangent = models.cross(shaft, anchor)
                    self.assertLess(models.dot(tangent, connection), 0)
                    opposite = tuple(-x for x in anchor)
                    self.assertEqual(models.cross(shaft, opposite), tuple(-x for x in tangent))

if __name__ == "__main__":
    unittest.main()
