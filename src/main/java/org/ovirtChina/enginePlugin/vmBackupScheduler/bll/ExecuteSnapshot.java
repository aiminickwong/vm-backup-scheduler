package org.ovirtChina.enginePlugin.vmBackupScheduler.bll;

import java.io.IOException;
import java.util.Date;
import java.util.TimerTask;
import java.util.UUID;

import org.apache.http.client.ClientProtocolException;
import org.ovirt.engine.sdk.Api;
import org.ovirt.engine.sdk.decorators.VM;
import org.ovirt.engine.sdk.entities.Snapshot;
import org.ovirt.engine.sdk.exceptions.ServerException;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.Task;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.TaskStatus;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.TaskType;
import org.ovirtChina.enginePlugin.vmBackupScheduler.dao.DbFacade;
import org.ovirtChina.enginePlugin.vmBackupScheduler.utils.OVirtEngineSDKUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecuteSnapshot extends TimerTask {
    private static Logger log = LoggerFactory.getLogger(ExecuteSnapshot.class);

    @Override
    public void run() {
        Api api = null;
        try {
            peformAction(api);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (api != null) {
                try {
                    api.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void peformAction(Api api) throws ClientProtocolException, ServerException, IOException {
        Task taskToExec = DbFacade.getInstance().getTaskDAO().getOldestTaskTypeWithStatus(TaskType.CreateSnapshot, TaskStatus.EXECUTING);
        if (taskToExec == null) {
            taskToExec = DbFacade.getInstance().getTaskDAO().getOldestTaskTypeWithStatus(TaskType.CreateSnapshot, TaskStatus.WAITING);
        }
        if (taskToExec == null) {
            log.info("There is no snapshot task to execute.");
            return;
        }
        api = OVirtEngineSDKUtils.getApi();
        if (api != null) {
            if (taskToExec.getTaskStatus() == TaskStatus.WAITING.getValue()) {
                Snapshot snap = new Snapshot();
                VM vm = api.getVMs().get(taskToExec.getVmID());
                snap.setVm(vm);
                snap.setDescription("autoSnap");
                String snapshotId = vm.getSnapshots().add(snap).getId();
                taskToExec.setBackupName(snapshotId);
                setTaskStatus(taskToExec, TaskStatus.EXECUTING);
                try {
                    querySnapshot(api, taskToExec, vm);
                } catch (InterruptedException e) {
                    log.error("Error while snapshoting vm: " + vm.getName(), e);
                    setTaskStatus(taskToExec, TaskStatus.FAILED);
                }
                setTaskStatus(taskToExec, TaskStatus.FINISHED);
            }
        }
    }

    private void querySnapshot(Api api, Task taskToExec, VM vm) throws ClientProtocolException, ServerException, IOException, InterruptedException {
        while(!api.getVMs().get(UUID.fromString(vm.getId())).getSnapshots().getById(taskToExec.getBackupName()).getSnapshotStatus().equals("ok")) {
            log.info("vm: " + vm.getName() + " is snapshoting, waiting for next query...");
            Thread.sleep(5000);
        }
    }

    private void setTaskStatus(Task taskToExec, TaskStatus taskStatus) {
        taskToExec.setTaskStatus(taskStatus.getValue());
        taskToExec.setLastUpdate(new Date());
        DbFacade.getInstance().getTaskDAO().update(taskToExec);
    }
}
