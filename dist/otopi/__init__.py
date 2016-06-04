"""vm backup scheduler plugin."""


from otopi import util


from . import vbssetup


@util.export
def createPlugins(context):
    vbssetup.Plugin(context=context)


# vim: expandtab tabstop=4 shiftwidth=4
