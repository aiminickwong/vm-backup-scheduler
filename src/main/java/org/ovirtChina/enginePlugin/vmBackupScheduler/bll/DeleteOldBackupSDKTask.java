package org.ovirtChina.enginePlugin.vmBackupScheduler.bll;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.apache.http.client.ClientProtocolException;
import org.ovirt.engine.sdk.Api;
import org.ovirt.engine.sdk.decorators.VM;
import org.ovirt.engine.sdk.exceptions.ServerException;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.AutoDeleteReservePolicy;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.BackupMethod;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.Task;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.VmPolicy;
import org.ovirtChina.enginePlugin.vmBackupScheduler.dao.DbFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DeleteOldBackupSDKTask extends TimerSDKTask {
    protected static Logger log = LoggerFactory.getLogger(DeleteOldBackupSDKTask.class);
    private static long dayMillis = 1000L * 3600L * 24L;
    int taskType;

    public DeleteOldBackupSDKTask(Api api, int taskType) {
        super(api);
        this.taskType = taskType;
    }

    @Override
    protected void peformAction() throws Exception {
        if (api != null) {
            List<VM> vms = api.getVMs().list();
            for (VM vm : vms) {
                List<Task> finishedTasks = DbFacade.getInstance()
                        .getTaskDAO().getAllFinishedTasksForVm(UUID.fromString(vm.getId()), taskType);
                if (finishedTasks != null && finishedTasks.size() > 0) {
                    VmPolicy vmPolicy = DbFacade.getInstance()
                            .getVmPolicyDAO().get(UUID.fromString(vm.getId()), taskType);
                    if (vmPolicy != null) {
                        switch (AutoDeleteReservePolicy.forValue(vmPolicy.getAutoDeleteReservePolicy())) {
                        case Quantity:
                            if (finishedTasks.size() > vmPolicy.getAutoDeleteReserveAmount()) {
                                log.debug("deleting vm " + vm.getName() + "'s old " + BackupMethod.forValue(taskType) + " backup " + finishedTasks.get(0).getBackupName());
                                deleteTask(finishedTasks.get(0));
                            }
                        case Day:
                            if (finishedTasks.get(0).getCreateTime().getTime() + dayMillis * vmPolicy.getAutoDeleteReserveAmount()
                                    < System.currentTimeMillis()) {
                                log.debug("deleting vm " + vm.getName() + "'s old " + BackupMethod.forValue(taskType) + " backup " + finishedTasks.get(0).getBackupName());
                                deleteTask(finishedTasks.get(0));
                            }
                        default:
                            break;
                        }
                    }
                } else {
                    log.debug("no " + BackupMethod.forValue(taskType) + " backup is needed to be deleted.");
                }
            }
        }
    }

    protected abstract void deleteTask(Task task) throws ClientProtocolException, ServerException, IOException, InterruptedException;

}
