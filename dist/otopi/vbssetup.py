"""vm backup scheduler setup."""

import os

from otopi import plugin, util

from ovirt_engine_setup import constants as osetupcons
from ovirt_engine_setup.engine import constants as oenginecons


@util.export
class Plugin(plugin.PluginBase):
    """vm backup scheduler setup."""

    @plugin.event(
        stage=plugin.Stages.STAGE_CLOSEUP,
        after=(
            osetupcons.Stages.DIALOG_TITLES_E_SUMMARY,
        ),
        condition=lambda self: (
            self.environment[oenginecons.EngineDBEnv.NEW_DATABASE]
        ),
    )
    def enable_vm_backup_scheduler_plugin(self):
        version = self.environment[
                oenginecons.ConfigEnv.EAYUNOS_VERSION
            ]
        if version == 'BaseVersion':
            os.system("yum remove -y engine-vm-backup")
        else:
            os.system("sed -i '/Defaults    requiretty/c\#Defaults    requiretty' /etc/sudoers")
            os.system("vm-backup-setup --password=%s"
                % self.environment[oenginecons.ConfigEnv.ADMIN_PASSWORD])
            os.system("sed -i '/#Defaults    requiretty/c\Defaults    requiretty' /etc/sudoers")
            self.dialog.note(text="vm backup scheduler enabled.")

    @plugin.event(
        stage=plugin.Stages.STAGE_CLOSEUP,
        after=(
            osetupcons.Stages.DIALOG_TITLES_E_SUMMARY,
        ),
        condition=lambda self: (
            not self.environment[oenginecons.EngineDBEnv.NEW_DATABASE]
        ),
    )
    def restart_vm_backup_scheduler_plugin(self):
        os.system("service engine-vm-backup restart")
