"""Generate connector roll models from their existing blockstate poses."""
import copy
import itertools
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/vsfluidlink"
NAMES = (
    "hose_connector", "magnet_hose_connector", "electric_wire_connector",
    "electric_magnet_wire_connector", "item_hose_connector",
    "item_magnet_hose_connector", "chain_connector", "magnet_chain_connector",
)
NORMALS = dict(north=(0, 0, -1), south=(0, 0, 1), east=(1, 0, 0),
               west=(-1, 0, 0), up=(0, 1, 0), down=(0, -1, 0))
UV_AXES = dict(north=((-1, 0, 0), (0, -1, 0)),
               south=((1, 0, 0), (0, -1, 0)),
               east=((0, 0, -1), (0, -1, 0)),
               west=((0, 0, 1), (0, -1, 0)),
               up=((1, 0, 0), (0, 0, 1)),
               down=((1, 0, 0), (0, 0, -1)))

def dot(a, b):
    return sum(x * y for x, y in zip(a, b))

def cross(a, b):
    return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])

def turn(v, axis, steps):
    for _ in range(steps % 4):
        c = cross(axis, v)
        d = dot(axis, v)
        v = tuple(c[i] + axis[i]*d for i in range(3))
    return tuple(round(x, 6) for x in v)

def transform(v, pose, axis, roll):
    v = turn(v, (1, 0, 0), -pose.get("x", 0)//90)
    v = turn(v, (0, 1, 0), -pose.get("y", 0)//90)
    return turn(v, axis, roll)

def point(v, pose, axis, roll):
    v = transform(tuple(x-8 for x in v), pose, axis, roll)
    return [round(x+8, 6) for x in v]

def load_model(name):
    namespace, path = name.split(":")
    if namespace != "vsfluidlink":
        raise ValueError("Unexpected model namespace: " + name)
    data = json.loads((ASSETS / "models" / (path + ".json")).read_text(encoding="utf-8"))
    if "elements" not in data:
        parent = load_model(data["parent"])
        parent.update({k: v for k, v in data.items() if k != "textures"})
        parent["textures"].update(data.get("textures", {}))
        data = parent
    return data

def rotate_model(data, pose, axis, roll):
    result = copy.deepcopy(data)
    result.pop("groups", None)
    for element in result["elements"]:
        corners = [point(v, pose, axis, roll) for v in itertools.product(
            *zip(element["from"], element["to"]))]
        element["from"] = [min(v[i] for v in corners) for i in range(3)]
        element["to"] = [max(v[i] for v in corners) for i in range(3)]
        if "rotation" in element:
            rotation = element["rotation"]
            rotation["origin"] = point(rotation["origin"], pose, axis, roll)
            basis = [0, 0, 0]
            basis["xyz".index(rotation["axis"])] = 1
            rotated = transform(basis, pose, axis, roll)
            idx = next(i for i, x in enumerate(rotated) if x)
            rotation["axis"] = "xyz"[idx]
            rotation["angle"] *= rotated[idx]
        faces = {}
        for old_name, face in element["faces"].items():
            normal = transform(NORMALS[old_name], pose, axis, roll)
            new_name = next(k for k, n in NORMALS.items() if n == normal)
            u = transform(UV_AXES[old_name][0], pose, axis, roll)
            new_u, new_v = UV_AXES[new_name]
            orientations = (new_u, new_v, tuple(-x for x in new_u), tuple(-x for x in new_v))
            delta = orientations.index(u)*90
            face["rotation"] = (face.get("rotation", 0) + delta) % 360
            if "cullface" in face:
                rotated = transform(NORMALS[face["cullface"]], pose, axis, roll)
                face["cullface"] = next(k for k, n in NORMALS.items() if n == rotated)
            faces[new_name] = face
        element["faces"] = faces
    return result

def generate():
    count = 0
    for name in NAMES:
        state_path = ASSETS / "blockstates" / (name + ".json")
        states = json.loads(state_path.read_text(encoding="utf-8"))
        # Original poses remain the source of truth; rerunning is idempotent.
        source = {k.removesuffix(",roll=0"): v for k, v in states["variants"].items()
                  if ",roll=" not in k or k.endswith(",roll=0")}
        if name in ("hose_connector", "electric_wire_connector"):
            # The original models point west; bake vertical base poses from
            # the established north pose so old horizontal states stay intact.
            for key, pose in list(source.items()):
                if "facing=north" not in key:
                    continue
                for facing, steps in (("up", 1), ("down", 3)):
                    new_key = key.replace("facing=north", "facing=" + facing)
                    generated = "block/roll_base/" + name + "_" + new_key.replace("=", "_").replace(",", "_")
                    target = ASSETS / "models" / (generated + ".json")
                    target.parent.mkdir(parents=True, exist_ok=True)
                    data = rotate_model(load_model(pose["model"]), pose, NORMALS["east"], steps)
                    target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
                    source[new_key] = {"model": "vsfluidlink:" + generated}
        variants = {}
        for key, pose in source.items():
            if not isinstance(pose, dict):
                raise ValueError("Unsupported variant: " + key)
            facing = dict(item.split("=") for item in key.split(","))["facing"]
            for roll in range(4):
                if roll == 0:
                    variants[key + ",roll=0"] = pose
                    continue
                model = pose["model"]
                suffix = "_".join(key.replace("=", "_").split(","))
                generated = "block/roll/" + name + "_" + suffix + "_" + str(roll)
                target = ASSETS / "models" / (generated + ".json")
                target.parent.mkdir(parents=True, exist_ok=True)
                data = rotate_model(load_model(model), pose, NORMALS[facing], roll)
                target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
                variants[key + ",roll=" + str(roll)] = {"model": "vsfluidlink:" + generated}
                count += 1
        states["variants"] = variants
        state_path.write_text(json.dumps(states, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Generated", count, "roll models")

if __name__ == "__main__":
    generate()
