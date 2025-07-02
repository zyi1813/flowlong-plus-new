
package cn.yuencode.flowlongplus.workflow.service;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 *  流程执行任务 扩展服务
 * @author Java-Zhangyi
 * @date 2025/06/25
 */
@Service
@AllArgsConstructor
public class FlowLongExecuteTaskService {
    private final FlowLongEngine flowLongEngine;

    /**
     * 执行当前活跃用户
     *
     * @param instanceId  流程实例ID
     * @param testCreator 任务创建者
     */
    public void executeActiveTasks(Long instanceId, FlowCreator testCreator) {
        this.executeActiveTasks(instanceId, testCreator, null);
    }

    public void executeActiveTasks(Long instanceId, FlowCreator testCreator, Map<String, Object> args) {
        this.executeActiveTasks(instanceId, t -> flowLongEngine.executeTask(t.getId(), testCreator, args));
    }

    public void executeActiveTasks(Long instanceId, Consumer<FlwTask> taskConsumer) {
        flowLongEngine.queryService().getActiveTasksByInstanceId(instanceId)
                .ifPresent(tasks -> tasks.forEach(taskConsumer));
    }

    public void executeTask(Long instanceId, FlowCreator flowCreator) {
        executeTask(instanceId, flowCreator, flwTask -> flowLongEngine.executeTask(flwTask.getId(), flowCreator));
    }

    public void executeTask(Long instanceId, FlowCreator flowCreator, Map<String, Object> args) {
        executeTask(instanceId, flowCreator, flwTask -> flowLongEngine.executeTask(flwTask.getId(), flowCreator, args));
    }

    public void executeTask(Long instanceId, FlowCreator flowCreator, Consumer<FlwTask> flwTaskConsumer) {
        QueryService queryService = flowLongEngine.queryService();
        List<FlwTask> flwTaskList = queryService.getTasksByInstanceId(instanceId);
        for (FlwTask flwTask : flwTaskList) {
            List<FlwTaskActor> taskActors = queryService.getTaskActorsByTaskId(flwTask.getId());
            if (null != taskActors && taskActors.stream()
                    // 找到当前对应审批的任务执行
                    .anyMatch(t -> Objects.equals(t.getActorId(), flowCreator.getCreateId()))) {
                flwTaskConsumer.accept(flwTask);
            }
        }
    }

    public void executeTaskByKey(Long instanceId, FlowCreator flowCreator, String nodeKey) {
        QueryService queryService = flowLongEngine.queryService();
        List<FlwTask> flwTaskList = queryService.getTasksByInstanceId(instanceId);
        flwTaskList.stream().filter(flwTask -> flwTask.getTaskKey().equals(nodeKey)).findFirst()
                .ifPresent(flwTask -> flowLongEngine.executeTask(flwTask.getId(), flowCreator));
    }
}
