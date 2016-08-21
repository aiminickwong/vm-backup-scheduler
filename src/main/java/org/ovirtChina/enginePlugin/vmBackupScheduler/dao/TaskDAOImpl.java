package org.ovirtChina.enginePlugin.vmBackupScheduler.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.ovirtChina.enginePlugin.vmBackupScheduler.common.Task;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.TaskStatus;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.TaskType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public class TaskDAOImpl extends CrudDAO<Task>{

    public Task getOldestTaskTypeWithStatus(TaskType taskType, TaskStatus taskStatus) {
        List<Task> result = DbFacade.getInstance().executeReadList("getOldestTaskTypeWithStatus", instance, new MapSqlParameterSource()
                .addValue("v_task_type", taskType.getValue())
                .addValue("v_task_status", taskStatus.getValue()));
        if (result != null && result.size() > 0) {
            return result.get(0);
        }
        return null;
    }

    public List<Task> getExecutingTasksForVm(UUID vmId) {
        List<Task> result = DbFacade.getInstance().executeReadList("getExecutingTasksForVm", instance, createIdParametersMapper(vmId));
        if (result != null) {
            return result;
        }
        return null;
    }

    public List<Task> getAllFinishedTasksForVm(UUID vmId, int taskType) {
        return DbFacade.getInstance().executeReadList("getAllFinishedTasksForVm", instance,
                createIdParametersMapper(vmId).addValue("v_task_type", taskType));
    }

    public void delete(UUID vmId, String backupName) {
        DbFacade.getInstance().executeModification("deleteTask",
                createIdParametersMapper(vmId).addValue("v_backup_name", backupName));
    }

    public void delete(UUID id) {
        return;
    }

    public TaskDAOImpl() {
        instance = new RowMapper<Task>() {

            public Task mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new Task(((UUID)rs.getObject("id")),
                    rs.getInt("task_status"),
                    rs.getInt("task_type"),
                    rs.getString("backup_name"),
                    rs.getTimestamp("create_time"),
                    rs.getTimestamp("last_update"));
            }
        };
        entityName = "Task";
    }

    public MapSqlParameterSource createFullParametersMapper(Task task) {
        return new MapSqlParameterSource().addValue("v_id", task.getVmID())
                .addValue("v_task_status", task.getTaskStatus())
                .addValue("v_task_type", task.getTaskType())
                .addValue("v_backup_name", task.getBackupName())
                .addValue("v_create_time", task.getCreateTime())
                .addValue("v_last_update", task.getLastUpdate());
    }

    public MapSqlParameterSource createIdParametersMapper(UUID id) {
        return new MapSqlParameterSource().addValue("v_id", id);
    }
}
