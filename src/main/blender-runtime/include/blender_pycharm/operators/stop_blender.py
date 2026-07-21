import bpy
from ..communication import register_post_action
from .. import log


LOG = log.getLogger()


def stop_action(data):
    LOG.info("Stopping Blender at the IDE's request.")
    bpy.ops.wm.quit_blender()


def register():
    register_post_action("stop", stop_action)
