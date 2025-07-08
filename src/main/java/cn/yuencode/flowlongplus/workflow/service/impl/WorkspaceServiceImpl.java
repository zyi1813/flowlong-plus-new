package cn.yuencode.flowlongplus.workflow.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.yuencode.flowlongplus.config.exception.CommonException;
import cn.yuencode.flowlongplus.config.mybatis.ApiContext;
import cn.yuencode.flowlongplus.entity.SysFileDetail;
import cn.yuencode.flowlongplus.entity.SysUser;
import cn.yuencode.flowlongplus.entity.SysUserRole;
import cn.yuencode.flowlongplus.mapper.FileDetailMapper;
import cn.yuencode.flowlongplus.mapper.SysUserMapper;
import cn.yuencode.flowlongplus.mapper.SysUserRoleMapper;
import cn.yuencode.flowlongplus.util.page.PageResultConvert;
import cn.yuencode.flowlongplus.workflow.config.WorkflowProperties;
import cn.yuencode.flowlongplus.workflow.context.BusinessOperationContext;
import cn.yuencode.flowlongplus.workflow.controller.req.*;
import cn.yuencode.flowlongplus.workflow.controller.res.CcRes;
import cn.yuencode.flowlongplus.workflow.controller.res.DoneRes;
import cn.yuencode.flowlongplus.workflow.controller.res.MyApplyRes;
import cn.yuencode.flowlongplus.workflow.controller.res.TaskRes;
import cn.yuencode.flowlongplus.workflow.dto.WFlowUserDto;
import cn.yuencode.flowlongplus.workflow.entity.WflowExtInstance;
import cn.yuencode.flowlongplus.workflow.entity.WflowInstanceActionRecord;
import cn.yuencode.flowlongplus.workflow.entity.WflowProcessTemplates;
import cn.yuencode.flowlongplus.workflow.enums.ActorType;
import cn.yuencode.flowlongplus.workflow.enums.FlwBusinessKeyEnum;
import cn.yuencode.flowlongplus.workflow.enums.WflowActionType;
import cn.yuencode.flowlongplus.workflow.factory.BusinessOperationFactory;
import cn.yuencode.flowlongplus.workflow.handler.BusinessOperationHandler;
import cn.yuencode.flowlongplus.workflow.mapper.WflowExtInstanceMapper;
import cn.yuencode.flowlongplus.workflow.mapper.WflowInstanceActionRecordMapper;
import cn.yuencode.flowlongplus.workflow.mapper.WflowProcessTemplatesMapper;
import cn.yuencode.flowlongplus.workflow.service.FlowLongExecuteTaskService;
import cn.yuencode.flowlongplus.workflow.service.WorkspaceService;
import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.FlowLongExpression;
import com.aizuda.bpm.engine.assist.Assert;
import com.aizuda.bpm.engine.assist.ObjectUtils;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.InstanceState;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.*;
import com.aizuda.bpm.engine.model.ConditionNode;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.engine.model.ProcessModel;
import com.aizuda.bpm.mybatisplus.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 工作台 服务实现类
 *
 * <p>
 * 尊重知识产权，不允许非法使用，后果自负
 * </p>
 *
 * @author 贾小宇
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {
    private final SysUserMapper sysUserMapper;
    private final ApiContext apiContext;
    private final FlowLongEngine flowLongEngine;
    private final WflowProcessTemplatesMapper wflowProcessTemplatesMapper;
    private final FlwTaskMapper flwTaskMapper;
    private final FlwHisTaskMapper flwHisTaskMapper;
    private final FlwTaskActorMapper flwTaskActorMapper;
    private final FlwHisTaskActorMapper flwHisTaskActorMapper;
    private final FlwHisInstanceMapper flwHisInstanceMapper;
    private final FlwProcessMapper flwProcessMapper;
    private final WflowInstanceActionRecordMapper wflowInstanceActionRecordMapper;
    private final FileDetailMapper fileDetailMapper;
    private final WflowExtInstanceMapper wflowExtInstanceMapper;
    private final FlwExtInstanceMapper flwExtInstanceMapper;
    private final FlwInstanceMapper flwInstanceMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final FlowLongExecuteTaskService flowLongExecuteTaskService;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final WorkflowProperties workflowProperties;
    /**
     * 启动流程实例
     * 根据流程模板创建并启动一个新的流程实例
     *
     * @param startProcessReq 启动流程请求参数，包含模板ID和流程变量
     * @throws CommonException 当用户不存在、流程模板不存在、流程未接入引擎或版本不一致时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startProcess(StartProcessReq startProcessReq) {
        String loginId = (String) StpUtil.getLoginId();
        SysUser sysUser = sysUserMapper.selectById(loginId);
        if (null == sysUser) {
            log.error("用户:{} 不存在", loginId);
            throw new CommonException("非法用户");
        }
        FlowCreator creator = FlowCreator.of(String.valueOf(apiContext.getCurrentTenantId()),
                loginId, sysUser.getNickname());
        WflowProcessTemplates temp = wflowProcessTemplatesMapper.selectById(startProcessReq.getTemplateId());
        WflowProcessTemplates template = wflowProcessTemplatesMapper.selectOne(Wrappers.<WflowProcessTemplates>lambdaQuery()
                .eq(WflowProcessTemplates::getTemplateKey, temp.getTemplateKey())
                .apply("(template_key, template_version) in(" +
                        "SELECT template_key, MAX(template_version) " +
                        "FROM wflow_process_templates " +
                        "GROUP BY template_key)")
        );
        if (ObjectUtil.isNull(template)) {
            log.error("流程模板不存在:{}", startProcessReq.getTemplateId());
            throw new CommonException("流程模板不存在");
        }
        FlwProcess flwProcess = flowLongEngine.processService().getProcessById(Long.valueOf(template.getWorkflowId()));
        if (ObjectUtil.isNull(flwProcess)) {
            log.error("流程未接入流程引擎:{}", Long.valueOf(template.getWorkflowId()));
            throw new CommonException("流程未接入流程引擎");
        }
        if (!Objects.equals(template.getWorkflowVersion(), flwProcess.getProcessVersion())) {
            log.error("与流程引擎版本不一致");
            throw new CommonException("流程引擎版本不一致");
        }

        // 验证发起人权限
        NodeModel nodeConfig = flwProcess.model().getNodeConfig();
        if (!hasInitiatorPermission(nodeConfig, loginId, sysUser)) {
            log.error("用户:{} 没有权限发起此流程", sysUser.getNickname());
            throw new CommonException("您没有权限发起此流程");
        }

        // 开启流程
        flowLongEngine.startInstanceById(flwProcess.getId(), creator, startProcessReq.getVariable()).ifPresent(instance -> {
            // action记录
            WflowInstanceActionRecord wflowInstanceActionRecord = new WflowInstanceActionRecord();
            wflowInstanceActionRecord.setInstanceId(String.valueOf(instance.getId()));
            wflowInstanceActionRecord.setActionType(WflowActionType.start.getValue());
            wflowInstanceActionRecord.setAuditorId(creator.getCreateId());
            wflowInstanceActionRecordMapper.insert(wflowInstanceActionRecord);

            // 添加拓展信息
            WflowExtInstance wflowExtInstance = new WflowExtInstance();
            wflowExtInstance.setId(String.valueOf(instance.getId()));
            wflowExtInstance.setTemplateId(startProcessReq.getTemplateId());
            wflowExtInstance.setProcessId(flwProcess.getId().toString());
            // 由于flowlong更新flw_process流程模版时不会更新id，所以这里保存一下version
            // 以便后续查询使用
            wflowExtInstance.setProcessVersion(flwProcess.getProcessVersion());
            wflowExtInstanceMapper.insert(wflowExtInstance);
        });

    }

    /**
     * 查询流程模板详情
     * 根据模板ID获取流程模板的详细信息
     *
     * @param templateId 流程模板ID
     * @return 流程模板详细信息
     * @throws CommonException 当流程模板不存在或流程未接入引擎时抛出
     */
    @Override
    public WflowProcessTemplates processDetail(String templateId) {
        WflowProcessTemplates template = wflowProcessTemplatesMapper.selectById(templateId);
        if (ObjectUtil.isNull(template)) {
            log.error("流程模板不存在:{}", templateId);
            throw new CommonException("非法数据");
        }

        FlwProcess flwProcess = flowLongEngine.processService().getProcessById(Long.valueOf(template.getWorkflowId()));
        if (ObjectUtil.isNull(flwProcess)) {
            log.error("流程未接入流程引擎:{}", Long.valueOf(template.getWorkflowId()));
            throw new CommonException("非法数据");
        }
        return template;
    }

    /**
     * 递归解析流程节点
     * 根据流程定义模型和执行参数，递归解析流程节点结构
     *
     * @param node            流程定义模型
     * @param processNodeList 流程节点列表，用于存储解析结果
     * @param args            执行参数，用于条件分支判断
     */
    void parseProcess(NodeModel node, List<Map<String, Object>> processNodeList, Map<String, Object> args) {
        if (node == null) {
            return;
        }
        NodeModel next = node.getChildNode();
        // 条件分支
        List<ConditionNode> conditionNodes = node.getConditionNodes();
        if (ObjectUtil.isNotEmpty(conditionNodes)) {
            Assert.illegal(ObjectUtils.isEmpty(args), "Execution parameter cannot be empty");
            FlowLongExpression expression = flowLongEngine.getContext().getFlowLongExpression();
            Assert.isNull(expression, "Interface Expression not implemented");
            Optional<ConditionNode> conditionNodeOptional = conditionNodes.stream().sorted(Comparator.comparing(ConditionNode::getPriorityLevel))
                    .filter(t -> expression.eval(t.getConditionList(), args)).findFirst();
            if (!conditionNodeOptional.isPresent()) {
                // 未发现满足条件分支，使用默认条件分支
                conditionNodeOptional = conditionNodes.stream().filter(t -> ObjectUtils.isEmpty(t.getConditionList())).findFirst();
                Assert.isFalse(conditionNodeOptional.isPresent(), "Not found executable ConditionNode");
            }
            ConditionNode conditionNode = conditionNodeOptional.orElse(null);
            parseProcess(conditionNode.getChildNode(), processNodeList, args);
        } else {
            Map<String, Object> map = BeanUtil.beanToMap(node,
                    "nodeName", "type", "examineMode", "nodeAssigneeList");
            // 结束节点
            if (node.getType() == -1) {
                return;
            }
            List<String> userIds = new ArrayList<>();
            if (ObjectUtil.isNotEmpty(node.getNodeAssigneeList())) {
                userIds = node.getNodeAssigneeList().stream().map(NodeAssignee::getId)
                        .collect(Collectors.toList());
            }
            map.put("userIds", userIds);
            map.put("setType", node.getSetType());
            map.put("name", node.getNodeName());
            processNodeList.add(map);
        }
        parseProcess(next, processNodeList, args);
    }

    /**
     * 流程过程预览
     * 根据启动参数预览流程的执行路径和节点信息
     *
     * @param params 流程启动参数，包含模板ID和流程变量
     * @return 流程预览结果，包含节点列表和执行路径
     */
    @Override
    public Object processPreview(StartProcessReq params) {
        List<Map<String, Object>> resp = new ArrayList<>();
        Map<String, Object> args = params.getVariable();
        WflowProcessTemplates template = wflowProcessTemplatesMapper.selectById(params.getTemplateId());
        if (ObjectUtil.isNull(template)) {
            log.error("流程模板不存在:{}", params.getTemplateId());
            throw new CommonException("非法数据");
        }
        FlwProcess flwProcess = flowLongEngine.processService().getProcessById(Long.valueOf(template.getWorkflowId()));
        if (ObjectUtil.isNull(flwProcess)) {
            log.error("流程未接入流程引擎:{}", Long.valueOf(template.getWorkflowId()));
            throw new CommonException("非法数据");
        }
        NodeModel currentNode = flwProcess.model().getNodeConfig();
        parseProcess(currentNode, resp, args);

        return resp;
    }

    /**
     * 获取待审批任务列表
     * 查询当前用户作为参与者（用户、角色、部门）的待处理任务
     *
     * @param queryParams 查询参数
     * @return 分页的待审批任务列表
     */
    @Override
    public Object getTodoTask(ProcessListReq queryParams) {
        // 获取当前用户信息
        String loginId = (String) StpUtil.getLoginId();
        SysUser sysUser = sysUserMapper.selectById(loginId);

        // 获取用户角色ID列表
        List<String> roleIds = sysUserRoleMapper.selectList(
                        Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, loginId))
                .stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());

        // 获取用户部门ID
        String deptId = sysUser.getDeptId();

        // 构建流程ID过滤条件
        List<String> processIds = buildProcessIdFilter(queryParams);

        // 构建实例ID过滤条件
        List<Long> instanceIds = buildInstanceIdFilter(queryParams, processIds);

        // 构建任务参与者查询条件
        LambdaQueryWrapper<FlwTaskActor> taskActorQuery = buildTaskActorQuery(loginId, roleIds, deptId, instanceIds);

        // 分页查询任务参与者
        Page<FlwTaskActor> taskActorPage = flwTaskActorMapper.selectPage(
                Page.of(queryParams.getPageNum(), queryParams.getPageSize()),
                taskActorQuery);

        if (taskActorPage.getRecords().isEmpty()) {
            return PageResultConvert.convertMybatisPlus(new Page<TaskRes>());
        }

        // 获取任务ID列表
        List<Long> taskIds = taskActorPage.getRecords().stream()
                .map(FlwTaskActor::getTaskId)
                .collect(Collectors.toList());

        // 查询任务详情
        List<FlwTask> tasks = flwTaskMapper.selectList(
                Wrappers.<FlwTask>lambdaQuery()
                        .orderByDesc(FlwTask::getCreateTime)
                        .in(FlwTask::getId, taskIds));

        // 构建返回结果
        List<TaskRes> taskResList = buildTaskResList(tasks);

        // 构建分页结果
        Page<TaskRes> resultPage = new Page<>();
        resultPage.setTotal(taskActorPage.getTotal());
        resultPage.setCurrent(taskActorPage.getCurrent());
        resultPage.setRecords(taskResList);

        return PageResultConvert.convertMybatisPlus(resultPage);
    }

    /**
     * 获取我发起的流程列表
     * 查询当前用户作为发起人的流程实例
     *
     * @param queryParams 查询参数
     * @return 分页的我发起的流程列表
     */
    @Override
    public Object getApplyList(ProcessListReq queryParams) {
        // 构建流程实例查询条件
        QueryWrapper<FlwHisInstance> instanceQuery = new QueryWrapper<>();
        instanceQuery.eq("create_id", StpUtil.getLoginId())
                .orderByDesc("create_time");

        // 添加实例状态过滤
        if (ObjectUtil.isNotNull(queryParams.getInstanceState())) {
            instanceQuery.eq("instance_state", queryParams.getInstanceState());
        }

        // 构建流程ID过滤条件
        List<String> processIds = buildProcessIdFilter(queryParams);

        // 添加流程ID过滤
        if (!processIds.isEmpty()) {
            instanceQuery.in("process_id", processIds);
        }

        // 分页查询流程实例
        Page<FlwHisInstance> instancePage = flwHisInstanceMapper.selectPage(
                Page.of(queryParams.getPageNum(), queryParams.getPageSize()),
                instanceQuery);

        // 构建返回结果
        List<MyApplyRes> applyList = instancePage.getRecords().stream()
                .map(this::buildMyApplyRes)
                .collect(Collectors.toList());

        // 构建分页结果
        Page<MyApplyRes> resultPage = new Page<>();
        resultPage.setTotal(instancePage.getTotal());
        resultPage.setCurrent(instancePage.getCurrent());
        resultPage.setSize(instancePage.getSize());
        resultPage.setRecords(applyList);

        return PageResultConvert.convertMybatisPlus(resultPage);
    }

    /**
     * 获取抄送我的流程列表
     * 查询当前用户作为抄送人的流程任务
     *
     * @param queryParams 查询参数
     * @return 分页的抄送流程列表
     */
    @Override
    public Object getCcList(ProcessListReq queryParams) {
        // 构建流程实例查询条件
        QueryWrapper<FlwHisInstance> instanceQuery = new QueryWrapper<>();
        instanceQuery.orderByDesc("create_time");

        // 添加实例状态过滤
        if (ObjectUtil.isNotNull(queryParams.getInstanceState())) {
            instanceQuery.eq("instance_state", queryParams.getInstanceState());
        }

        // 构建流程ID过滤条件
        List<String> processIds = buildProcessIdFilter(queryParams);

        // 构建实例ID过滤条件
        List<Long> instanceIds = buildInstanceIdFilter(queryParams, processIds);

        // 构建历史任务参与者查询条件（抄送类型）
        LambdaQueryWrapper<FlwHisTaskActor> taskActorQuery = Wrappers.<FlwHisTaskActor>lambdaQuery()
                .eq(FlwHisTaskActor::getActorType, 0)  // 用户类型
                .eq(FlwHisTaskActor::getActorId, StpUtil.getLoginId());

        if (!instanceIds.isEmpty()) {
            taskActorQuery.in(FlwHisTaskActor::getInstanceId, instanceIds);
        }

        // 查询历史任务参与者
        List<FlwHisTaskActor> taskActors = flwHisTaskActorMapper.selectList(taskActorQuery);

        if (taskActors.isEmpty()) {
            return PageResultConvert.convertMybatisPlus(new Page<CcRes>());
        }

        // 获取任务ID列表
        List<Long> taskIds = taskActors.stream()
                .map(FlwTaskActor::getTaskId)
                .collect(Collectors.toList());

        // 分页查询历史任务（抄送类型）
        Page<FlwHisTask> taskPage = flwHisTaskMapper.selectPage(
                Page.of(queryParams.getPageNum(), queryParams.getPageSize()),
                Wrappers.<FlwHisTask>lambdaQuery()
                        .eq(FlwHisTask::getTaskType, 2)  // 抄送任务类型
                        .in(FlwHisTask::getId, taskIds));

        // 构建返回结果
        List<CcRes> ccList = taskPage.getRecords().stream()
                .map(this::buildCcRes)
                .collect(Collectors.toList());

        // 构建分页结果
        Page<CcRes> resultPage = new Page<>();
        resultPage.setCurrent(taskPage.getCurrent());
        resultPage.setSize(taskPage.getSize());
        resultPage.setTotal(taskPage.getTotal());
        resultPage.setRecords(ccList);

        return PageResultConvert.convertMybatisPlus(resultPage);
    }

    /**
     * 获取已审批的流程列表
     *
     * @param queryParams 查询参数
     * @return 分页的已审批流程列表
     */
    @Override
    public Object getDoneList(ProcessListReq queryParams) {
        // 构建流程实例查询条件
        QueryWrapper<FlwHisInstance> instanceQuery = new QueryWrapper<>();
        instanceQuery.orderByDesc("create_time");

        // 添加实例状态过滤
        if (ObjectUtil.isNotNull(queryParams.getInstanceState())) {
            instanceQuery.eq("instance_state", queryParams.getInstanceState());
        }

        // 构建流程ID过滤条件
        List<String> processIds = buildProcessIdFilter(queryParams);

        // 构建实例ID过滤条件
        List<Long> instanceIds = buildInstanceIdFilter(queryParams, processIds);

        // 构建历史任务参与者查询条件（所有类型）
        LambdaQueryWrapper<FlwHisTaskActor> taskActorQuery = Wrappers.<FlwHisTaskActor>lambdaQuery()
                .in(FlwHisTaskActor::getActorType, Arrays.asList(0, 1, 2))  // 用户、角色、部门类型
                .eq(FlwHisTaskActor::getActorId, StpUtil.getLoginId());

        if (!instanceIds.isEmpty()) {
            taskActorQuery.in(FlwHisTaskActor::getInstanceId, instanceIds);
        }

        // 查询历史任务参与者
        List<FlwHisTaskActor> taskActors = flwHisTaskActorMapper.selectList(taskActorQuery);

        if (taskActors.isEmpty()) {
            return PageResultConvert.convertMybatisPlus(new Page<DoneRes>());
        }

        // 获取任务ID列表
        List<Long> taskIds = taskActors.stream()
                .map(FlwTaskActor::getTaskId)
                .collect(Collectors.toList());

        // 分页查询历史任务（排除抄送和会签类型）
        Page<FlwHisTask> taskPage = flwHisTaskMapper.selectPage(
                Page.of(queryParams.getPageNum(), queryParams.getPageSize()),
                Wrappers.<FlwHisTask>lambdaQuery()
                        .notIn(FlwHisTask::getTaskType, TaskType.cc.getValue(), TaskType.major.getValue())
                        .orderByDesc(FlwHisTask::getCreateTime)
                        .in(FlwHisTask::getId, taskIds));

        // 构建返回结果
        List<DoneRes> doneList = taskPage.getRecords().stream()
                .map(this::buildDoneRes)
                .collect(Collectors.toList());

        // 构建分页结果
        Page<DoneRes> resultPage = new Page<>();
        resultPage.setCurrent(taskPage.getCurrent());
        resultPage.setSize(taskPage.getSize());
        resultPage.setTotal(taskPage.getTotal());
        resultPage.setRecords(doneList);

        return PageResultConvert.convertMybatisPlus(resultPage);
    }

    /**
     * 查询流程实例表单数据
     * 根据实例ID获取流程实例的表单数据和表单配置
     *
     * @param instanceId 流程实例ID
     * @return 包含表单配置和表单数据的Map对象
     * @throws CommonException 当流程实例不存在时抛出
     */
    @Override
    public Object getInstanceForm(String instanceId) {
        FlwHisInstance flwHisInstance = flwHisInstanceMapper.selectById(instanceId);
        if (ObjectUtil.isNull(flwHisInstance)) {
            throw new CommonException("非法数据");
        }
        WflowExtInstance wflowExtInstance = wflowExtInstanceMapper.selectOne(Wrappers.<WflowExtInstance>lambdaQuery()
                .eq(WflowExtInstance::getId, instanceId));
        WflowProcessTemplates wflowProcessTemplates = wflowProcessTemplatesMapper.selectOne(Wrappers.<WflowProcessTemplates>lambdaQuery()
                .eq(WflowProcessTemplates::getTemplateId, wflowExtInstance.getTemplateId()));
        Map<String, Object> resp = new HashMap<>();
        resp.put("formItems", wflowProcessTemplates.getFormItems());
        resp.put("formData", JSONUtil.parseObj(flwHisInstance.getVariable()));
        return resp;
    }

    /**
     * 同意审批任务
     * 处理用户对流程任务的同意操作，支持用户、角色、部门三种参与者类型
     *
     * @param processAgreeReq 同意审批请求参数，包含任务ID和审批意见
     * @throws CommonException 当任务不存在、用户不存在、任务参与者不存在或流程实例不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void agree(ProcessAgreeReq processAgreeReq) {
        // 1. 参数校验
        if (processAgreeReq == null || StringUtils.isBlank(processAgreeReq.getTaskId())) {
            throw new CommonException("请求参数不合法");
        }
        // 2. 获取并验证任务信息
        String taskId = processAgreeReq.getTaskId();
        FlwTask flwTask = this.getAndValidateTask(taskId);

        // 3. 获取并验证当前用户
        FlowCreator currentFlowCreator = this.getAndValidateCurrentUser();

        // 4. 处理任务（根据参与者类型）
        this.handleTaskByActorType(taskId, flwTask.getInstanceId(), currentFlowCreator);

        // 5. 记录操作
        this.recordApprovalAction(processAgreeReq, flwTask.getInstanceId());


        // 6. 触发业务操作
        this.triggerBusinessOperation(String.valueOf(flwTask.getInstanceId()));
    }
    /**
     * 触发业务操作
     * 如果是流程最后一个审核节点，异步触发业务操作
     *
     * @param instanceId 流程实例ID
     */
    private void triggerBusinessOperation(String instanceId) {
        try {
            // 获取流程实例信息
            FlwHisInstance flwHisInstance = flwHisInstanceMapper.selectById(instanceId);
            if (flwHisInstance == null) {
                log.error("流程历史实例不存在: {}", instanceId);
                return;
            }

            // 获取流程定义信息
            FlwProcess flwProcess = flwProcessMapper.selectById(flwHisInstance.getProcessId());
            if (flwProcess == null) {
                log.error("流程定义不存在: {}", flwHisInstance.getProcessId());
                return;
            }

            // 判断是否流程通过审核并完结
//            if (!isLastApprovalNode(flwHisInstance, flwProcess)) {
//                log.debug("当前节点不是最后一个审核节点，跳过业务操作触发");
//                return;
//            }
            if (!isProcessFinished(flwHisInstance)) {
                log.debug("流程未结束，跳过业务操作触发");
                return;
            }
            // 异步触发业务操作
            CompletableFuture.runAsync(() -> {
                try {
                    // 根据流程key匹配业务操作策略
                    String businessKey = matchBusinessKey(flwProcess.getProcessKey());
                    if (businessKey == null) {
                        log.warn("未找到匹配的业务操作策略，processKey: {}", flwProcess.getProcessKey());
                        return;
                    }
                    // 获取业务操作处理器并执行
                    BusinessOperationHandler handler = BusinessOperationFactory.getHandler(businessKey);
                    if (handler != null) {
                        BusinessOperationContext context = BusinessOperationContext.builder()
                                .instanceId(Long.valueOf(instanceId))
                                .processKey(flwProcess.getProcessKey())
                                .variable(flwHisInstance.getVariable())
                                .build();

                        handler.execute(context);
                    } else {
                        log.warn("未找到业务操作处理器，businessKey: {}", businessKey);
                    }
                } catch (Exception e) {
                    log.error("异步执行业务操作失败，instanceId: {} ", instanceId, e);
                }
            }, taskExecutor);

        } catch (Exception e) {
            log.error("触发业务操作失败，instanceId: {}", instanceId, e);
        }
    }


    /**
     * 获取并验证任务信息
     *
     * @param taskId 任务ID
     * @return 任务信息
     */
    private FlwTask getAndValidateTask(String taskId) {
        FlwTask flwTask = flwTaskMapper.selectById(taskId);
        if (flwTask == null) {
            log.error("任务不存在: {}", taskId);
            throw new CommonException("任务不存在");
        }
        return flwTask;
    }

    /**
     * 获取并验证当前用户
     *
     * @return 当前用户信息
     */
    private FlowCreator getAndValidateCurrentUser() {
        String loginId = (String) StpUtil.getLoginId();
        SysUser sysUser = sysUserMapper.selectById(loginId);

        if (sysUser == null) {
            log.error("用户不存在: {}", loginId);
            throw new CommonException("未知用户");
        }

        if (!sysUser.getIsEnable()) {
            log.error("用户账号已被禁用: {}", loginId);
            throw new CommonException("账号已被禁用，请联系管理员");
        }

        return FlowCreator.of(loginId, sysUser.getNickname());
    }

    /**
     * 根据参与者类型处理任务
     *
     * @param taskId             任务ID
     * @param instanceId         流程实例ID
     * @param currentFlowCreator 当前用户信息
     */
    private void handleTaskByActorType(String taskId, Long instanceId, FlowCreator currentFlowCreator) {
        List<FlwTaskActor> flwTaskActors = flwTaskActorMapper.selectList(
                new LambdaQueryWrapper<FlwTaskActor>().eq(FlwTaskActor::getTaskId, taskId));

        if (flwTaskActors.isEmpty()) {
            log.error("任务参与者不存在: {}", taskId);
            throw new CommonException("任务参与者不存在");
        }

        FlwInstance flwInstance = this.getAndValidateInstance(instanceId);

        ActorType actorType = ActorType.getEnumByValue(flwTaskActors.get(0).getActorType());
        if (actorType == null) {
            log.error("未知参与者类型: {}", flwTaskActors.get(0).getActorType());
            throw new CommonException("未知参与者类型");
        }

        switch (actorType) {
            case role:
                flowLongExecuteTaskService.executeActiveTasks(flwInstance.getId(),
                        t -> flowLongEngine.taskService().claimRole(t.getId(), currentFlowCreator));
                break;
            case department:
                flowLongExecuteTaskService.executeActiveTasks(flwInstance.getId(),
                        t -> flowLongEngine.taskService().claimDepartment(t.getId(), currentFlowCreator));
                break;
            case user:
            default:
                flowLongEngine.executeTask(Long.valueOf(taskId), currentFlowCreator);
                return; // 用户类型不需要执行下面的通用任务
        }

        // 角色和部门类型需要执行通用任务
        flowLongExecuteTaskService.executeActiveTasks(flwInstance.getId(), currentFlowCreator);
    }


    /**
     * 判断是否为最后一个审核节点
     *
     * @param flwTask    流程任务
     * @param flwProcess 流程定义
     * @return true 是最后一个审核节点，false 不是
     */
    private boolean isLastApprovalNode(FlwTask flwTask, FlwProcess flwProcess) {
        try {
            // 获取流程模型
            ProcessModel processModel = flwProcess.model();
            if (processModel == null || processModel.getNodeConfig() == null) {
                log.error("流程模型为空，processId: {}", flwProcess.getId());
                return false;
            }

            // 获取当前节点
            NodeModel currentNode = processModel.getNode(flwTask.getTaskKey());
            if (currentNode == null) {
                log.error("当前节点不存在，taskKey: {}", flwTask.getTaskKey());
                return false;
            }

            // 获取下一个节点
            Optional<NodeModel> nextNodeOptional = currentNode.nextNode();

            // 如果没有下一个节点，或者下一个节点是结束节点，则认为是最后一个审核节点
            if (!nextNodeOptional.isPresent()) {
                return true;
            }

            NodeModel nextNode = nextNodeOptional.get();

            // 如果下一个节点是结束节点，则当前节点是最后一个审核节点
            if (nextNode.endNode()) {
                return true;
            }

            // 如果下一个节点是抄送节点，继续查找下一个节点
            if (nextNode.ccNode()) {
                return isLastApprovalNodeAfterCc(nextNode, processModel);
            }

            return false;
        } catch (Exception e) {
            log.error("判断最后一个审核节点失败，taskId: {}", flwTask.getId(), e);
            return false;
        }
    }

    /**
     * 判断流程是否通过审核并完结
     *
     * @return true 流程审核结束，false 流程未结束
     */
    private boolean isProcessFinished(FlwHisInstance flwHisInstance) {
        Integer instanceState = flwHisInstance.getInstanceState();
        if (null == instanceState) {
            return false;
        }
        return instanceState == InstanceState.complete.getValue();
    }

    /**
     * 判断抄送节点后是否还有审核节点
     *
     * @param ccNode       抄送节点
     * @param processModel 流程模型
     * @return true 没有更多审核节点，false 还有审核节点
     */
    private boolean isLastApprovalNodeAfterCc(NodeModel ccNode, ProcessModel processModel) {
        Optional<NodeModel> nextNodeOptional = ccNode.nextNode();
        if (!nextNodeOptional.isPresent()) {
            return true;
        }

        NodeModel nextNode = nextNodeOptional.get();
        if (nextNode.endNode()) {
            return true;
        }

        if (nextNode.approvalOrMajor()) {
            return false;
        }

        if (nextNode.ccNode()) {
            return isLastApprovalNodeAfterCc(nextNode, processModel);
        }

        return true;
    }

    /**
     * 根据流程key匹配业务操作key
     *
     * @param processKey 流程key
     * @return 业务操作key
     */
    private String matchBusinessKey(String processKey) {
        if (StringUtils.isBlank(processKey)) {
            return null;
        }

        // 遍历枚举类 FlwBusinessKeyEnum 找到对应的业务类型
        for (FlwBusinessKeyEnum keyEnum : FlwBusinessKeyEnum.values()) {
            if (processKey.contains(keyEnum.getProcessKey())) {
                return keyEnum.getProcessKey();
            }
        }
        return null;
    }

    /**
     * 根据实例ID获取实例信息
     *
     * @param instanceId 实例ID
     * @return 流程实例信息
     */
    private FlwInstance getAndValidateInstance(Long instanceId) {
        FlwInstance flwInstance = flwInstanceMapper.selectById(instanceId);
        if (flwInstance == null) {
            log.error("实例不存在: {}", instanceId);
            throw new CommonException("流程实例不存在");
        }
        return flwInstance;
    }

    /**
     * 记录审批动作
     *
     * @param processAgreeReq 审批请求参数
     * @param instanceId      流程实例ID
     */
    private void recordApprovalAction(ProcessAgreeReq processAgreeReq, Long instanceId) {
        WflowInstanceActionRecord record = new WflowInstanceActionRecord();
        record.setInstanceId(String.valueOf(instanceId));
        record.setAuditorId(StpUtil.getLoginId().toString());
        record.setComment(processAgreeReq.getComment());
        record.setActionType(WflowActionType.approved.getValue());
        record.setAttachments(processAgreeReq.getAttachments());
        wflowInstanceActionRecordMapper.insert(record);
    }

    /**
     * 获取当前处理用户信息
     *
     * @return 当前用户信息
     */
    private FlowCreator currentFlowCreator() {
        String loginId = (String) StpUtil.getLoginId();
        SysUser sysUser = sysUserMapper.selectById(loginId);
        if (null == sysUser) {
            log.error("用户:{} 不存在", loginId);
            throw new CommonException("非法用户");
        }
        return FlowCreator.of(String.valueOf(apiContext.getCurrentTenantId()),
                loginId, sysUser.getNickname());
    }

    /**
     * 拒绝审批任务
     * 处理用户对流程任务的拒绝操作，直接拒绝当前流程
     *
     * @param processRefuseReq 拒绝审批请求参数，包含任务ID、拒绝原因和附件
     * @throws CommonException 当任务不存在时抛出
     */
    @Override
    public void refuse(ProcessRefuseReq processRefuseReq) {
        Long taskId = Long.valueOf(processRefuseReq.getTaskId());
        FlwTask flwTask = flwTaskMapper.selectById(taskId);
        // 通过 instanceId 查询流程实例，再查流程定义
        FlwInstance flwInstance = flwInstanceMapper.selectById(flwTask.getInstanceId());
        FlwProcess flwProcess = flwProcessMapper.selectById(flwInstance.getProcessId());
        NodeModel node = flwProcess.model().getNode(flwTask.getTaskKey());
        Integer rejectStrategy = node.getRejectStrategy();
        if (rejectStrategy != null) {
            if (rejectStrategy == 1) {
                // 驳回到发起人
                String startNodeKey = flwProcess.model().getNodeConfig().getNodeKey();
                flowLongEngine.executeRejectTask(flwTask, startNodeKey, currentFlowCreator(), null, false);
            } else if (rejectStrategy == 2) {
                // 驳回到上一级
                flowLongEngine.taskService().rejectTask(flwTask, currentFlowCreator());
            } else {
                // 直接结束流程（默认）
                flowLongEngine.runtimeService().reject(flwTask.getInstanceId(), currentFlowCreator());
            }
        } else {
            // 默认直接结束
            flowLongEngine.runtimeService().reject(flwTask.getInstanceId(), currentFlowCreator());
        }

        // 新增：插入实际办理人历史参与者，保证 角色/部门认领任务审核驳回后 能在已审批列表中查到
        List<FlwHisTask> hisTasks = flwHisTaskMapper.selectList(
                Wrappers.<FlwHisTask>lambdaQuery()
                        .eq(FlwHisTask::getInstanceId, flwTask.getInstanceId())
                        .eq(FlwHisTask::getId, taskId)
        );
        String loginId = (String) StpUtil.getLoginId();
        SysUser sysUser = sysUserMapper.selectById(loginId);
        if (!hisTasks.isEmpty() && sysUser != null) {
            FlwHisTask hisTask = hisTasks.get(0);
            FlwHisTaskActor realActor = new FlwHisTaskActor();
            realActor.setTenantId(hisTask.getTenantId());
            realActor.setInstanceId(hisTask.getInstanceId());
            realActor.setTaskId(hisTask.getId());
            realActor.setActorId(loginId);
            realActor.setActorName(sysUser.getNickname());
            realActor.setActorType(0); // 用户
            realActor.setWeight(1);
            flwHisTaskActorMapper.insert(realActor);
        }
        // 动作执行记录
        WflowInstanceActionRecord record = new WflowInstanceActionRecord();
        record.setInstanceId(flwTask.getInstanceId().toString());
        record.setAuditorId(StpUtil.getLoginId().toString());
        record.setComment(processRefuseReq.getComment());
        record.setActionType(WflowActionType.rejected.getValue());
        record.setAttachments(processRefuseReq.getAttachments());
        wflowInstanceActionRecordMapper.insert(record);
    }

    /**
     * 撤销流程实例
     * 撤销当前用户发起的流程实例，只能撤销审批中的流程
     *
     * @param processRevokeReq 撤销流程请求参数，包含实例ID、撤销原因和附件
     * @throws CommonException 当流程实例不存在、流程已结束或用户无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(ProcessRevokeReq processRevokeReq) {
        Long instanceId = Long.valueOf(processRevokeReq.getInstanceId());
        FlwHisInstance histInstance = flowLongEngine.queryService().getHistInstance(instanceId);
        if (ObjectUtil.isNull(histInstance)) {
            log.error("流程实例不存在:{}", instanceId);
            throw new CommonException("当前流程实例不存在");
        }
        if (histInstance.getInstanceState() != 0) {
            throw new CommonException("当前流程实例已结束");
        }
        flowLongEngine.runtimeService().revoke(instanceId, currentFlowCreator());
        // 动作执行记录
        WflowInstanceActionRecord record = new WflowInstanceActionRecord();
        record.setInstanceId(instanceId.toString());
        record.setAuditorId(StpUtil.getLoginId().toString());
        record.setComment(processRevokeReq.getComment());
        record.setNodeId(histInstance.getCurrentNodeKey());
        record.setActionType(WflowActionType.canceled.getValue());
        record.setAttachments(processRevokeReq.getAttachments());
        wflowInstanceActionRecordMapper.insert(record);
    }

    /**
     * 查询流程实例动作记录
     * 获取流程实例的所有操作记录，包括审批、拒绝、撤销、评论等操作
     *
     * @param instanceId 流程实例ID
     * @return 动作记录列表，包含操作类型、操作人、操作时间、评论、附件等信息
     * @throws CommonException 当流程实例不存在时抛出
     */
    @Override
    public Object getInstanceAction(String instanceId) {
        List<Map<String, Object>> list = new ArrayList<>();
        // 查询action
        List<WflowInstanceActionRecord> actions = wflowInstanceActionRecordMapper.selectList(Wrappers.<WflowInstanceActionRecord>lambdaQuery()
                .eq(WflowInstanceActionRecord::getInstanceId, instanceId)
                .orderByAsc(WflowInstanceActionRecord::getCreateTime));
        actions.forEach(action -> {
            Map<String, Object> map = BeanUtil.beanToMap(action);
            // 查询附件信息
            List<String> attachmentIds = action.getAttachments();
            List<Map<String, Object>> attachments = new ArrayList<>();
            if (ObjectUtil.isNotNull(attachmentIds)) {
                attachmentIds.forEach(attachmentId -> {
                    SysFileDetail sysFileDetail = fileDetailMapper.selectById(attachmentId);
                    Map<String, Object> attachment = new HashMap<>();
                    if (ObjectUtil.isNotNull(sysFileDetail)) {
                        attachment.put("id", sysFileDetail.getId());
                        attachment.put("filename", sysFileDetail.getFilename());
                        attachment.put("originalFilename", sysFileDetail.getOriginalFilename());
                        attachment.put("size", sysFileDetail.getSize());
                        attachment.put("url", sysFileDetail.getUrl());
                    }
                    attachments.add(attachment);
                });
            }
            // 更新附件信息
            map.put("attachments", attachments);
            list.add(map);
        });
        // 查询当前实例状态 如果是审批中 最后插入一个 审批中节点数据
        FlwHisInstance histInstance = flowLongEngine.queryService().getHistInstance(Long.valueOf(instanceId));
        if (ObjectUtil.isNull(histInstance)) {
            log.error("流程实例不存在:{}", instanceId);
            throw new CommonException("未知实例");
        }
        // 审批中
        if (histInstance.getInstanceState() == InstanceState.active.getValue()) {
            String currentNode = histInstance.getCurrentNodeKey();
            // 查询流程定义
            Long processId = histInstance.getProcessId();
            FlwProcess flwProcess = flwProcessMapper.selectById(processId);
            NodeModel node = flwProcess.model().getNode(currentNode);
            if (ObjectUtil.isNull(node)) {
                log.error("未找到节点：{}", currentNode);
                throw new CommonException("未知节点");
            }
            Map<String, Object> map = BeanUtil.beanToMap(node,
                    "nodeName", "type", "examineMode", "nodeAssigneeList");
            List<String> userIds = new ArrayList<>();
            if (ObjectUtil.isNotEmpty(node.getNodeAssigneeList())) {
                userIds = node.getNodeAssigneeList().stream().map(NodeAssignee::getId)
                        .collect(Collectors.toList());
            }
            map.put("userIds", userIds);
            map.put("setType", node.getSetType());
            map.put("underway", true);
            list.add(map);
        }
        return list;
    }

    /**
     * 查询流程节点信息
     * 根据流程ID和节点ID获取指定节点的详细信息
     *
     * @param processId 流程定义ID
     * @param nodeId    节点ID
     * @return 节点详细信息，包含节点配置、参与者、条件等
     * @throws CommonException 当流程定义不存在或节点不存在时抛出
     */
    @Override
    public Object getNodeInfo(String processId, String nodeId) {
        FlwProcess flwProcess = flwProcessMapper.selectById(Long.valueOf(processId));
        if (ObjectUtil.isNull(flwProcess)) {
            log.error("未找到流程定义：{}", processId);
            throw new CommonException("未知流程");
        }
        NodeModel node = flwProcess.model().getNode(nodeId);
        if (ObjectUtil.isNull(node)) {
            log.error("未找到节点：{}", nodeId);
            throw new CommonException("未知节点");
        }
        return this.deepConvertNodeModelToMap(node, null);
    }

    /**
     * 添加流程评论
     * 为流程实例添加评论信息，记录用户的意见或说明
     *
     * @param params 评论请求参数，包含实例ID、评论内容和附件
     * @throws CommonException 当流程实例不存在时抛出
     */
    @Override
    public void comment(ProcessCommentReq params) {
        FlwHisInstance flwHisInstance = flwHisInstanceMapper.selectById(params.getInstanceId());
        if (ObjectUtil.isNull(flwHisInstance)) {
            log.error("未找到流程实例：{}", params.getInstanceId());
            throw new CommonException("未知实例");
        }
        // 动作执行记录
        WflowInstanceActionRecord record = new WflowInstanceActionRecord();
        record.setInstanceId(params.getInstanceId());
        record.setAuditorId(StpUtil.getLoginId().toString());
        record.setComment(params.getComment());
        record.setActionType(WflowActionType.comment.getValue());
        record.setAttachments(params.getAttachments());
        wflowInstanceActionRecordMapper.insert(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(ResubmitProcessReq req) {
        // 支持发起人节点再次提交时，允许更新所有表单数据
        Long instanceId = Long.valueOf(req.getInstanceId());
        FlwHisInstance histInstance = flowLongEngine.queryService().getHistInstance(instanceId);
        if (ObjectUtil.isNull(histInstance)) {
            throw new CommonException("流程实例不存在");
        }
        // 校验当前节点为发起人
        FlwProcess flwProcess = flwProcessMapper.selectById(histInstance.getProcessId());
        NodeModel node = flwProcess.model().getNode(histInstance.getCurrentNodeKey());
        if (node.getType() != 0) {
            throw new CommonException("当前节点不是发起人节点，不能再次提交");
        }
        // 校验当前用户为发起人
        String loginId = (String) StpUtil.getLoginId();
        if (!loginId.equals(histInstance.getCreateId())) {
            throw new CommonException("只有发起人可以再次提交");
        }
        // 更新表单数据（支持全量覆盖）
        histInstance.setVariable(JSONUtil.toJsonStr(req.getFormData()));
        flwHisInstanceMapper.updateById(histInstance);
        // 获取当前发起人节点的待办任务ID
        List<FlwTask> tasks = flwTaskMapper.selectList(
                Wrappers.<FlwTask>lambdaQuery()
                        .eq(FlwTask::getInstanceId, instanceId)
                        .eq(FlwTask::getTaskKey, node.getNodeKey())
        );
        if (CollectionUtil.isEmpty(tasks)) {
            throw new CommonException("未找到发起人节点的待办任务");
        }
        Long taskId = tasks.get(0).getId();
        // 重新流转
        flowLongEngine.executeTask(taskId, currentFlowCreator());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建流程ID过滤条件
     * 根据分组ID和流程名称查询对应的流程ID列表
     *
     * @param queryParams 查询参数
     * @return 流程ID列表
     */
    private List<String> buildProcessIdFilter(ProcessListReq queryParams) {
        List<String> processIds = new ArrayList<>();

        // 根据分组ID查询流程
        if (StrUtil.isNotEmpty(queryParams.getGroupId())) {
            WflowProcessTemplates queryTemplate = new WflowProcessTemplates();
            queryTemplate.setGroupId(queryParams.getGroupId());
            List<WflowProcessTemplates> templates = wflowProcessTemplatesMapper.getList(queryTemplate);
            List<String> workflowIds = templates.stream()
                    .map(WflowProcessTemplates::getWorkflowId)
                    .collect(Collectors.toList());
            processIds.addAll(workflowIds);
        }

        // 根据流程名称查询流程
        if (StrUtil.isNotEmpty(queryParams.getProcessName())) {
            List<FlwProcess> processes = flwProcessMapper.selectList(
                    Wrappers.<FlwProcess>lambdaQuery()
                            .like(FlwProcess::getProcessName, queryParams.getProcessName()));
            List<String> pIds = processes.stream()
                    .map(item -> item.getId().toString())
                    .collect(Collectors.toList());
            processIds.addAll(pIds);
        }

        return processIds;
    }

    /**
     * 构建实例ID过滤条件
     * 根据流程ID列表查询对应的实例ID列表
     *
     * @param queryParams 查询参数
     * @param processIds  流程ID列表
     * @return 实例ID列表
     */
    private List<Long> buildInstanceIdFilter(ProcessListReq queryParams, List<String> processIds) {
        // 如果没有流程过滤条件，返回空列表
        if (processIds.isEmpty() && StrUtil.isEmpty(queryParams.getGroupId())
                && StrUtil.isEmpty(queryParams.getProcessName())) {
            return new ArrayList<>();
        }

        // 如果有流程过滤条件但流程ID为空，返回空列表
        if (processIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询对应的实例ID
        LambdaQueryWrapper<FlwHisInstance> instanceQuery = new LambdaQueryWrapper<>();
        instanceQuery.in(FlwHisInstance::getProcessId, processIds);

        return flwHisInstanceMapper.selectList(instanceQuery).stream()
                .map(FlwHisInstance::getId)
                .collect(Collectors.toList());
    }

    /**
     * 构建任务参与者查询条件
     * 根据用户ID、角色ID列表、部门ID和实例ID列表构建查询条件
     *
     * @param loginId     用户ID
     * @param roleIds     角色ID列表
     * @param deptId      部门ID
     * @param instanceIds 实例ID列表
     * @return 查询条件
     */
    private LambdaQueryWrapper<FlwTaskActor> buildTaskActorQuery(String loginId, List<String> roleIds,
                                                                 String deptId, List<Long> instanceIds) {
        LambdaQueryWrapper<FlwTaskActor> query = Wrappers.lambdaQuery();

        // 构建参与者条件：用户、角色、部门
        query.and(w -> {
            // 直接分配给当前用户
            w.eq(FlwTaskActor::getActorId, loginId).eq(FlwTaskActor::getActorType, 0);

            // 分配给角色
            if (CollectionUtil.isNotEmpty(roleIds)) {
                w.or(wq -> wq.in(FlwTaskActor::getActorId, roleIds).eq(FlwTaskActor::getActorType, 1));
            }

            // 分配给部门
            if (StrUtil.isNotEmpty(deptId)) {
                w.or(wq -> wq.eq(FlwTaskActor::getActorId, deptId).eq(FlwTaskActor::getActorType, 2));
            }
        });

        // 添加实例ID过滤
        if (!instanceIds.isEmpty()) {
            query.in(FlwTaskActor::getInstanceId, instanceIds);
        }

        return query;
    }

    /**
     * 构建任务响应列表
     * 将任务列表转换为前端需要的响应格式
     *
     * @param tasks 任务列表
     * @return 任务响应列表
     */
    private List<TaskRes> buildTaskResList(List<FlwTask> tasks) {
        return tasks.stream().map(task -> {
            // 查询流程实例信息
            FlwHisInstance histInstance = flowLongEngine.queryService().getHistInstance(task.getInstanceId());
            if (ObjectUtil.isNull(histInstance)) {
                throw new CommonException("非法数据");
            }

            // 查询流程定义信息
            FlwProcess flwProcess = flwProcessMapper.selectById(histInstance.getProcessId());

            return TaskRes.builder()
                    .taskId(String.valueOf(task.getId()))
                    .taskName(task.getTaskName())
                    .instanceId(histInstance.getId().toString())
                    .processId(histInstance.getProcessId().toString())
                    .instanceState(histInstance.getInstanceState().toString())
                    .taskState("0")
                    .currentNodeName(histInstance.getCurrentNodeName())
                    .currentNodeKey(histInstance.getCurrentNodeKey())
                    .duration(histInstance.getDuration())
                    .processName(flwProcess.getProcessName())
                    .startTime(histInstance.getCreateTime())
                    .startUser(WFlowUserDto.builder()
                            .id(histInstance.getCreateId())
                            .name(histInstance.getCreateBy())
                            .build())
                    .taskStartTime(task.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 构建我的申请响应对象
     *
     * @param histInstance 历史实例
     * @return 我的申请响应对象
     */
    private MyApplyRes buildMyApplyRes(FlwHisInstance histInstance) {
        FlwProcess flwProcess = flwProcessMapper.selectById(histInstance.getProcessId());
        return MyApplyRes.builder()
                .processName(flwProcess.getProcessName())
                .processId(histInstance.getProcessId().toString())
                .currentNodeName(histInstance.getCurrentNodeName())
                .currentNodeKey(histInstance.getCurrentNodeKey())
                .instanceId(histInstance.getId().toString())
                .instanceState(histInstance.getInstanceState().toString())
                .startTime(histInstance.getCreateTime())
                .startUser(WFlowUserDto.builder()
                        .id(histInstance.getCreateId())
                        .name(histInstance.getCreateBy())
                        .build())
                .duration(histInstance.getDuration())
                .build();
    }

    /**
     * 构建抄送响应对象
     *
     * @param histTask 历史任务
     * @return 抄送响应对象
     */
    private CcRes buildCcRes(FlwHisTask histTask) {
        // 查询流程实例信息
        FlwHisInstance histInstance = flowLongEngine.queryService().getHistInstance(histTask.getInstanceId());
        if (ObjectUtil.isNull(histInstance)) {
            throw new CommonException("非法数据");
        }

        // 查询流程定义信息
        FlwProcess flwProcess = flwProcessMapper.selectById(histInstance.getProcessId());

        return CcRes.builder()
                .taskId(String.valueOf(histTask.getId()))
                .taskName(histTask.getTaskName())
                .instanceId(histInstance.getId().toString())
                .processId(histInstance.getProcessId().toString())
                .instanceState(histInstance.getInstanceState().toString())
                .taskState(histTask.getTaskState().toString())
                .currentNodeName(histInstance.getCurrentNodeName())
                .currentNodeKey(histInstance.getCurrentNodeKey())
                .duration(histInstance.getDuration())
                .processName(flwProcess.getProcessName())
                .startTime(histInstance.getCreateTime())
                .startUser(WFlowUserDto.builder()
                        .id(histInstance.getCreateId())
                        .name(histInstance.getCreateBy())
                        .build())
                .taskStartTime(histTask.getCreateTime())
                .build();
    }

    /**
     * 构建已审批响应对象
     *
     * @param histTask 历史任务
     * @return 已审批响应对象
     */
    private DoneRes buildDoneRes(FlwHisTask histTask) {
        // 查询流程实例信息
        FlwHisInstance histInstance = flowLongEngine.queryService().getHistInstance(histTask.getInstanceId());
        if (ObjectUtil.isNull(histInstance)) {
            throw new CommonException("非法数据");
        }

        // 查询流程定义信息
        FlwProcess flwProcess = flwProcessMapper.selectById(histInstance.getProcessId());

        return DoneRes.builder()
                .taskId(String.valueOf(histTask.getId()))
                .taskName(histTask.getTaskName())
                .instanceId(histInstance.getId().toString())
                .processId(histInstance.getProcessId().toString())
                .instanceState(histInstance.getInstanceState().toString())
                .taskState(histTask.getTaskState().toString())
                .currentNodeName(histInstance.getCurrentNodeName())
                .currentNodeKey(histInstance.getCurrentNodeKey())
                .duration(histInstance.getDuration())
                .processName(flwProcess.getProcessName())
                .startTime(histInstance.getCreateTime())
                .startUser(WFlowUserDto.builder()
                        .id(histInstance.getCreateId())
                        .name(histInstance.getCreateBy())
                        .build())
                .taskStartTime(histTask.getCreateTime())
                .build();
    }

    /**
     * 递归转换节点模型为 Map，用于前端展示。
     * 当 args 不为 null 时，它会进入"预测模式"，只递归处理和渲染符合 args 条件的分支。
     * 当 args 为 null 时，它会进入"展示模式"，渲染所有节点及其所有分支，用于展示流程的完整结构。
     *
     * @param node 节点
     * @param args 参数
     * @return map 对象
     */
    private Map<String, Object> deepConvertNodeModelToMap(NodeModel node, Map<String, Object> args) {
        if (node == null) {
            return null;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        // 手动复制所有必需的属性以避免递归并控制输出
        map.put("nodeName", node.getNodeName());
        map.put("nodeKey", node.getNodeKey()); // Important for frontend to identify nodes
//        map.put("callProcessKey", node.getCallProcessKey());
        map.put("type", node.getType());
        map.put("setType", node.getSetType());
        map.put("nodeAssigneeList", node.getNodeAssigneeList());
        if (node.getNodeAssigneeList() != null) {
            map.put("userIds", node.getNodeAssigneeList().stream().map(NodeAssignee::getId).collect(Collectors.toList()));
        } else {
            map.put("userIds", new ArrayList<>());
        }
        map.put("examineLevel", node.getExamineLevel());
        map.put("directorLevel", node.getDirectorLevel());
        map.put("selectMode", node.getSelectMode());
        map.put("term", node.getTerm());
        map.put("termMode", node.getTermMode());
        map.put("examineMode", node.getExamineMode());
        map.put("directorMode", node.getDirectorMode());
        map.put("passWeight", node.getPassWeight());
        map.put("remind", node.getRemind());
//        map.put("allowSelection", node.isAllowSelection());
//        map.put("allowTransfer", node.isAllowTransfer());
//        map.put("allowAppendNode", node.isAllowAppendNode());
//        map.put("allowRollback", node.isAllowRollback());
//        map.put("approveSelf", node.isApproveSelf());
        map.put("extendConfig", node.getExtendConfig());

        // 递归转换 childNode
        if (node.getChildNode() != null) {
            map.put("childNode", deepConvertNodeModelToMap(node.getChildNode(), args));
        }

        // 递归转换 conditionNodes
        if (CollectionUtil.isNotEmpty(node.getConditionNodes())) {
            List<Map<String, Object>> conditionNodeMaps;
            if (args != null) {
                conditionNodeMaps = new ArrayList<>();
                // 如果有参数，说明是预测模式，只展示命中的分支
                FlowLongExpression expression = flowLongEngine.getContext().getFlowLongExpression();
                node.getConditionNodes().stream()
                        .filter(cn -> expression.eval(cn.getConditionList(), args) || ObjectUtils.isEmpty(cn.getConditionList()))
                        .findFirst()
                        .ifPresent(cn -> conditionNodeMaps.add(deepConvertConditionNodeToMap(cn, args)));
            } else {
                // 如果没有参数，说明是展示模式，展示所有分支
                conditionNodeMaps = node.getConditionNodes().stream()
                        .map(cn -> deepConvertConditionNodeToMap(cn, null))
                        .collect(Collectors.toList());
            }
            map.put("conditionNodes", conditionNodeMaps);
        }

        return map;
    }

    /**
     * 递归转换条件节点为 Map
     * 将条件节点对象转换为前端可用的 Map 格式，包含条件信息和子节点
     *
     * @param conditionNode 条件节点对象
     * @param args          执行参数，用于条件判断（可为null）
     * @return 转换后的条件节点 Map 对象
     */
    private Map<String, Object> deepConvertConditionNodeToMap(ConditionNode conditionNode, Map<String, Object> args) {
        if (conditionNode == null) {
            return null;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nodeKey", conditionNode.getNodeKey());
        map.put("nodeName", conditionNode.getNodeName());
        map.put("priorityLevel", conditionNode.getPriorityLevel());
        map.put("conditionList", conditionNode.getConditionList());

        // 递归转换 childNode for the condition
        if (conditionNode.getChildNode() != null) {
            map.put("childNode", deepConvertNodeModelToMap(conditionNode.getChildNode(), args));
        }

        return map;
    }

    /**
     * 检查用户是否有发起流程的权限
     *
     * @param nodeModel 流程节点模型
     * @param userId 用户ID
     * @param sysUser 用户信息
     * @return true 有权限，false 无权限
     */
    private boolean hasInitiatorPermission(NodeModel nodeModel, String userId, SysUser sysUser) {
        // 超级管理员白名单直接放行
        if (CollectionUtil.isNotEmpty(workflowProperties.getSuperAdminWhitelist()) && workflowProperties.getSuperAdminWhitelist().contains(userId)) {
            return true;
        }
        // 递归查找发起人节点（type=0）
        NodeModel initiatorNode = findInitiatorNode(nodeModel);
        if (initiatorNode == null) {
            // 没有找到发起人节点，默认所有人都有权限
            return true;
        }

        List<NodeAssignee> nodeAssigneeList = initiatorNode.getNodeAssigneeList();
        if (CollectionUtil.isEmpty(nodeAssigneeList)) {
            // 发起人节点没有指定参与者，默认所有人都有权限
            return true;
        }

        // 根据节点的setType判断参与者类型
        Integer setType = initiatorNode.getSetType();
        if (setType == null) {
            // 如果没有设置setType，默认所有人都有权限
            return true;
        }

        // 获取用户角色ID列表
        List<String> userRoleIds = sysUserRoleMapper.selectList(
                        Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());

        // 获取用户部门ID
        String userDeptId = sysUser.getDeptId();

        // 检查用户是否在指定的参与者列表中
        for (NodeAssignee assignee : nodeAssigneeList) {
            String assigneeId = assignee.getId();

            if (setType == 1) {
                // 指定成员：直接匹配用户ID
                if (Objects.equals(userId, assigneeId)) {
                    return true;
                }
            } else if (setType == 3) {
                // 角色：检查用户是否属于该角色
                if (userRoleIds.contains(assigneeId)) {
                    return true;
                }
            } else if (setType == 7) {
                // 部门：检查用户是否属于该部门
                if (Objects.equals(userDeptId, assigneeId)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 递归查找发起人节点
     *
     * @param nodeModel 节点模型
     * @return 发起人节点，如果没找到返回null
     */
    private NodeModel findInitiatorNode(NodeModel nodeModel) {
        if (nodeModel == null) {
            return null;
        }

        // 检查当前节点是否为发起人节点
        if (nodeModel.getType() != null && nodeModel.getType() == 0) {
            return nodeModel;
        }

        // 递归检查子节点
        if (nodeModel.getChildNode() != null) {
            NodeModel childResult = findInitiatorNode(nodeModel.getChildNode());
            if (childResult != null) {
                return childResult;
            }
        }

        // 递归检查条件节点
        if (CollectionUtil.isNotEmpty(nodeModel.getConditionNodes())) {
            for (ConditionNode conditionNode : nodeModel.getConditionNodes()) {
                if (conditionNode.getChildNode() != null) {
                    NodeModel childResult = findInitiatorNode(conditionNode.getChildNode());
                    if (childResult != null) {
                        return childResult;
                    }
                }
            }
        }

        return null;
    }
}
