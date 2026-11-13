"""차툰 그림 시험: 3D 카툰 차 한 장면을 Blender로 만들어 렌더한다(외부 자산 없이 도형만으로).

실행: blender -b -P car_test.py -- <나올 파일.png> [차 색 #rrggbb] [day|dusk|night]
"""
import math
import sys

import bpy
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if len(argv) > 0 else "/tmp/car.png"
BODY_HEX = argv[1] if len(argv) > 1 else "#E8503A"
TIME = argv[2] if len(argv) > 2 else "day"


def rgb(hex_color: str, alpha: float = 1.0):
    h = hex_color.lstrip("#")
    # sRGB → 선형(재질 색은 선형 값으로 넣어야 화면에서 같은 색이 된다)
    def lin(c):
        c = c / 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    return (lin(int(h[0:2], 16)), lin(int(h[2:4], 16)), lin(int(h[4:6], 16)), alpha)


def material(name: str, hex_color: str, rough: float = 0.55, alpha: float = 1.0, emit: float = 0.0, metallic: float = 0.0):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = rgb(hex_color)
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metallic
    if alpha < 1.0:
        bsdf.inputs["Alpha"].default_value = alpha
        mat.surface_render_method = "BLENDED"
    if emit > 0:
        bsdf.inputs["Emission Color"].default_value = rgb(hex_color)
        bsdf.inputs["Emission Strength"].default_value = emit
    return mat


def smooth(obj):
    for poly in obj.data.polygons:
        poly.use_smooth = True


def box(name, size, loc, mat, bevel=0.1, segments=6, subsurf=0, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    obj = bpy.context.object
    obj.name = name
    obj.scale = size
    bpy.ops.object.transform_apply(scale=True)
    if bevel > 0:
        mod = obj.modifiers.new("Bevel", "BEVEL")
        mod.width = bevel
        mod.segments = segments
    if subsurf:
        obj.modifiers.new("Subsurf", "SUBSURF").levels = subsurf
        obj.modifiers["Subsurf"].render_levels = subsurf
    smooth(obj)
    obj.data.materials.append(mat)
    return obj


def ball(name, radius, loc, mat, scale=(1, 1, 1)):
    bpy.ops.mesh.primitive_uv_sphere_add(radius=radius, location=loc, segments=48, ring_count=24)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    smooth(obj)
    obj.data.materials.append(mat)
    return obj


def cylinder(name, radius, depth, loc, mat, rot=(math.pi / 2, 0, 0), bevel=0.03):
    bpy.ops.mesh.primitive_cylinder_add(radius=radius, depth=depth, location=loc, rotation=rot, vertices=48)
    obj = bpy.context.object
    obj.name = name
    if bevel > 0:
        mod = obj.modifiers.new("Bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 4
    smooth(obj)
    obj.data.materials.append(mat)
    return obj


# ---------------------------------------------------------------- 빈 장면
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene

SKY = {
    "day": dict(sky="#8CCBF5", horizon="#DDF1FF", sun="#FFF4DC", sun_power=4.2, ambient=1.0, sun_rot=(48, 0, 130)),
    "dusk": dict(sky="#F7A66B", horizon="#FFD9A8", sun="#FF9A55", sun_power=3.0, ambient=0.7, sun_rot=(74, 0, 110)),
    "night": dict(sky="#141B3A", horizon="#27305A", sun="#8FA6FF", sun_power=0.7, ambient=0.25, sun_rot=(40, 0, 150)),
}[TIME]

# ---------------------------------------------------------------- 재질
body = material("body", BODY_HEX, rough=0.32)
roof = material("roof", BODY_HEX, rough=0.32)
glass = material("glass", "#D6F1FF", rough=0.03, alpha=0.13)
tire = material("tire", "#1E1F24", rough=0.85)
hub = material("hub", "#DADDE2", rough=0.35, metallic=0.2)
white = material("white", "#FFFFFF", rough=0.25, emit=0.6 if TIME == "night" else 0.0)
pupil = material("pupil", "#15161A", rough=0.2)
cheek = material("cheek", "#FF9E9E", rough=0.6)
dark = material("dark", "#2A2C33", rough=0.6)
skin = material("skin", "#F3C9A5", rough=0.7)
hair = material("hair", "#3A2A22", rough=0.8)
shirt = material("shirt", "#FFD23F", rough=0.8)
seat = material("seat", "#6B6F7B", rough=0.9)
road = material("road", "#4B4F5A", rough=0.9)
line = material("line", "#F4F4F0", rough=0.8)
curb = material("curb", "#C9CDD3", rough=0.9)
walk = material("walk", "#E3E0D8", rough=0.95)
leaf = material("leaf", "#6DBE6E", rough=0.9)
trunk_mat = material("trunk", "#8A6A4A", rough=0.9)
lamp = material("lamp", "#FFE9A8", rough=0.4, emit=4.0 if TIME != "day" else 0.0)

# ---------------------------------------------------------------- 차(앞이 +X)
box("lower", (3.1, 1.66, 0.8), (0, 0, 0.67), body, bevel=0.36, segments=12)
# 캐빈: 지붕과 기둥만 두고 유리는 얇은 판으로 끼워 안이 보이게 한다.
box("roof", (1.6, 1.4, 0.2), (-0.27, 0, 1.74), roof, bevel=0.095, segments=8)
for x, tilt in ((0.5, -0.38), (-1.02, 0.3)):
    for y in (-0.66, 0.66):
        box("pillar", (0.09, 0.09, 0.78), (x + (0.13 if tilt < 0 else -0.1), y, 1.37), roof, bevel=0.03, segments=3, rot=(0, tilt, 0))
box("windshield", (0.03, 1.3, 0.72), (0.66, 0, 1.36), glass, bevel=0, rot=(0, -0.38, 0))
box("rear_glass", (0.03, 1.3, 0.7), (-1.14, 0, 1.36), glass, bevel=0, rot=(0, 0.3, 0))
for y in (-0.7, 0.7):
    box("side_glass", (1.42, 0.03, 0.56), (-0.25, y, 1.4), glass, bevel=0)
box("cabin_floor", (1.5, 1.36, 0.1), (-0.25, 0, 1.03), seat, bevel=0.03, segments=3)

# 사람(운전석은 차 기준 왼쪽 = +Y). 앞유리 너머로 얼굴이 보이게 크게 둔다.
DRIVER_Y = 0.34
box("seat_back", (0.14, 0.52, 0.66), (-0.5, DRIVER_Y, 1.34), seat, bevel=0.06, segments=4)
box("seat_back2", (0.14, 0.52, 0.66), (-0.5, -DRIVER_Y, 1.34), seat, bevel=0.06, segments=4)
box("torso", (0.34, 0.5, 0.46), (-0.2, DRIVER_Y, 1.2), shirt, bevel=0.15, segments=6)
ball("head", 0.235, (-0.1, DRIVER_Y, 1.56), skin)
ball("hair", 0.245, (-0.16, DRIVER_Y, 1.61), hair, scale=(1.0, 1.02, 0.84))
for dy in (-0.085, 0.085):
    ball("person_eye", 0.032, (0.118, DRIVER_Y + dy, 1.59), pupil)
    ball("person_cheek", 0.04, (0.1, DRIVER_Y + dy * 1.75, 1.51), cheek, scale=(0.4, 1, 0.7))
bpy.ops.mesh.primitive_torus_add(major_radius=0.18, minor_radius=0.026, location=(0.3, DRIVER_Y, 1.26), rotation=(0, math.radians(66), 0))
bpy.context.object.data.materials.append(dark)
smooth(bpy.context.object)
cabin_light_data = bpy.data.lights.new("cabin", "POINT")
cabin_light_data.energy = 55
cabin_light_data.shadow_soft_size = 0.5
cabin_light = bpy.data.objects.new("cabin", cabin_light_data)
cabin_light.location = (0.35, 0.0, 1.55)

# 바퀴
for x in (-0.98, 1.0):
    for y in (-0.8, 0.8):
        cylinder("tire", 0.4, 0.3, (x, y, 0.4), tire, bevel=0.07)
        cylinder("hub", 0.2, 0.32, (x, y, 0.4), hub, bevel=0.02)

# 얼굴: 전조등이 눈, 그릴이 입
for y in (-0.5, 0.5):
    ball("eye_white", 0.25, (1.47, y, 0.84), white, scale=(0.5, 1, 1))
    ball("eye_pupil", 0.115, (1.587, y * 0.9, 0.85), pupil, scale=(0.5, 1, 1))
    ball("eye_glint", 0.038, (1.64, y * 0.9 + 0.045, 0.9), white, scale=(0.5, 1, 1))
    ball("cheek", 0.13, (1.5, y * 1.36, 0.56), cheek, scale=(0.35, 1, 0.62))
    box("mirror", (0.16, 0.2, 0.15), (0.62, y * 1.82, 1.1), body, bevel=0.06, segments=4)
# 웃는 입: 고리의 아래쪽 절반
bpy.ops.mesh.primitive_torus_add(major_radius=0.17, minor_radius=0.028, location=(1.56, 0, 0.66), rotation=(0, math.radians(90), 0), major_segments=48)
mouth = bpy.context.object
mouth.data.materials.append(dark)
smooth(mouth)
bpy.ops.object.mode_set(mode="EDIT")
bpy.ops.mesh.select_all(action="DESELECT")
bpy.ops.object.mode_set(mode="OBJECT")
for v in mouth.data.vertices:
    v.select = (mouth.matrix_world @ v.co).z > 0.665
bpy.ops.object.mode_set(mode="EDIT")
bpy.ops.mesh.delete(type="VERT")
bpy.ops.object.mode_set(mode="OBJECT")
box("plate", (0.03, 0.46, 0.15), (1.565, 0, 0.42), white, bevel=0.02, segments=3)
box("bumper", (0.2, 1.5, 0.16), (1.48, 0, 0.3), dark, bevel=0.07, segments=5)

# ---------------------------------------------------------------- 무대
box("road", (60, 7.4, 0.1), (0, 0, -0.05), road, bevel=0)
for i in range(-12, 13):
    box("dash", (1.3, 0.14, 0.012), (i * 2.6, -1.85, 0.006), line, bevel=0)
box("edge_line", (60, 0.12, 0.012), (0, 1.9, 0.006), line, bevel=0)
box("curb", (60, 0.3, 0.18), (0, 3.85, 0.09), curb, bevel=0.04, segments=3)
box("walk", (60, 4.5, 0.16), (0, 6.2, 0.08), walk, bevel=0)
palette = ["#F6C8A0", "#BFD9F2", "#F2B8B0", "#CDE7C8", "#F5E3A6", "#D7C8F0"]
x = -17.0
i = 0
while x < 17:
    w = 2.6 + (i * 37 % 5) * 0.5
    h = 3.2 + (i * 53 % 7) * 0.75
    b = box("building", (w, 3.0, h), (x + w / 2, 10.2, h / 2), material(f"b{i}", palette[i % len(palette)], rough=0.9), bevel=0.06, segments=3)
    win = material(f"w{i}", "#FFE9A8" if TIME != "day" else "#7FA8C9", rough=0.3, emit=2.5 if TIME != "day" and i % 2 == 0 else 0.0)
    cols = max(2, int(w // 0.9))
    rows = max(2, int(h // 1.1))
    for cx in range(cols):
        for rz in range(rows):
            box("win", (0.42, 0.06, 0.5), (x + (cx + 0.5) * (w / cols), 8.69, 0.9 + rz * ((h - 1.2) / rows)), win, bevel=0)
    x += w + 0.25
    i += 1
for tx in (-7.5, -2.2, 3.4, 8.8):
    cylinder("tree_trunk", 0.12, 1.5, (tx, 5.3, 0.9), trunk_mat, rot=(0, 0, 0), bevel=0)
    ball("tree", 0.85, (tx, 5.3, 2.1), leaf, scale=(1, 1, 0.92))
    ball("tree2", 0.55, (tx + 0.45, 5.2, 1.75), leaf)
for lx in (-5.0, 6.0):
    cylinder("lamp_post", 0.05, 3.0, (lx, 4.3, 1.5), dark, rot=(0, 0, 0), bevel=0)
    ball("lamp", 0.2, (lx, 4.3, 3.05), lamp)

# ---------------------------------------------------------------- 빛과 하늘
world = bpy.data.worlds.new("sky")
world.use_nodes = True
nodes = world.node_tree.nodes
links = world.node_tree.links
background = nodes["Background"]
gradient = nodes.new("ShaderNodeTexGradient")
coord = nodes.new("ShaderNodeTexCoord")
mapping = nodes.new("ShaderNodeMapping")
ramp = nodes.new("ShaderNodeValToRGB")
mapping.inputs["Rotation"].default_value = (0, math.radians(-90), 0)
ramp.color_ramp.elements[0].position = 0.48
ramp.color_ramp.elements[0].color = rgb(SKY["horizon"])
ramp.color_ramp.elements[1].position = 0.75
ramp.color_ramp.elements[1].color = rgb(SKY["sky"])
links.new(coord.outputs["Generated"], mapping.inputs["Vector"])
links.new(mapping.outputs["Vector"], gradient.inputs["Vector"])
links.new(gradient.outputs["Color"], ramp.inputs["Fac"])
links.new(ramp.outputs["Color"], background.inputs["Color"])
background.inputs["Strength"].default_value = SKY["ambient"]
scene.world = world
scene.collection.objects.link(cabin_light)

sun_data = bpy.data.lights.new("sun", "SUN")
sun_data.energy = SKY["sun_power"]
sun_data.color = rgb(SKY["sun"])[:3]
sun_data.angle = math.radians(6)
sun = bpy.data.objects.new("sun", sun_data)
sun.rotation_euler = [math.radians(a) for a in SKY["sun_rot"]]
scene.collection.objects.link(sun)
if TIME == "night":
    for lx in (-5.0, 6.0):
        lamp_data = bpy.data.lights.new("street", "POINT")
        lamp_data.energy = 900
        lamp_data.color = (1.0, 0.86, 0.6)
        lamp_data.shadow_soft_size = 0.4
        lamp_obj = bpy.data.objects.new("street", lamp_data)
        lamp_obj.location = (lx, 4.0, 2.9)
        scene.collection.objects.link(lamp_obj)
    for y in (-0.5, 0.5):
        head = bpy.data.lights.new("headlight", "SPOT")
        head.energy = 500
        head.spot_size = math.radians(70)
        head.color = (1.0, 0.95, 0.8)
        head_obj = bpy.data.objects.new("headlight", head)
        head_obj.location = (1.7, y, 0.8)
        head_obj.rotation_euler = (0, math.radians(82), 0)
        scene.collection.objects.link(head_obj)

# ---------------------------------------------------------------- 카메라
camera_data = bpy.data.cameras.new("camera")
camera_data.lens = 44
camera = bpy.data.objects.new("camera", camera_data)
camera.location = (6.6, -5.0, 1.9)
target = Vector((0.1, 0.4, 1.05))
camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()
scene.collection.objects.link(camera)
scene.camera = camera

# ---------------------------------------------------------------- 렌더
scene.render.engine = "BLENDER_EEVEE"
scene.render.resolution_x = 1200
scene.render.resolution_y = 900
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = "PNG"
scene.render.filepath = OUT
try:
    scene.view_settings.view_transform = "Standard"
except TypeError as error:
    print("뷰 변환 설정 못 함:", error)
scene.view_settings.look = "None"
try:
    scene.eevee.taa_render_samples = 48
except AttributeError as error:
    print("샘플 설정 못 함:", error)
for name, value in (("use_shadows", True), ("use_raytracing", True)):
    if hasattr(scene.eevee, name):
        setattr(scene.eevee, name, value)
bpy.ops.render.render(write_still=True)
print("렌더 끝:", OUT)
