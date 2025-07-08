package cn.yuencode.flowlongplus.workflow.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.yuencode.flowlongplus.entity.SysUser;
import cn.yuencode.flowlongplus.entity.SysUserRole;
import cn.yuencode.flowlongplus.mapper.SysUserMapper;
import cn.yuencode.flowlongplus.mapper.SysUserRoleMapper;
import com.aizuda.bpm.engine.TaskActorProvider;
import com.aizuda.bpm.engine.assist.ObjectUtils;
import com.aizuda.bpm.engine.core.Execution;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.NodeSetType;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.aizuda.bpm.engine.model.NodeModel;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 自定义任务参与者提供者
 * 用于处理发起人节点的角色权限验证
 *
 * @author 贾小宇
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomTaskActorProvider implements TaskActorProvider {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    @Override
    public boolean isAllowed(NodeModel nodeModel, FlowCreator flowCreator) {
        // 如果是发起人节点，进行特殊权限验证
        if (TaskType.major.eq(nodeModel.getType())) {
            return checkInitiatorPermission(nodeModel, flowCreator);
        }
        
        // 其他节点使用默认逻辑
        List<NodeAssignee> nodeAssigneeList = nodeModel.getNodeAssigneeList();
        if (NodeSetType.specifyMembers.eq(nodeModel.getSetType()) && ObjectUtils.isNotEmpty(nodeAssigneeList)) {
            return nodeAssigneeList.stream().anyMatch(t -> Objects.equals(t.getId(), flowCreator.getCreateId()));
        }

        if (TaskType.major.eq(nodeModel.getType()) && !NodeSetType.initiatorSelected.eq(nodeModel.getSetType())) {
            // 发起人且非自选情况
            return true;
        }

        // 角色判断
        if (NodeSetType.role.eq(nodeModel.getSetType()) && ObjectUtils.isNotEmpty(nodeAssigneeList)) {
            return checkRolePermission(nodeModel, flowCreator);
        }

        // 部门判断
        if (NodeSetType.department.eq(nodeModel.getSetType()) && ObjectUtils.isNotEmpty(nodeAssigneeList)) {
            return checkDepartmentPermission(nodeModel, flowCreator);
        }

        return true;
    }

    @Override
    public List<FlwTaskActor> getTaskActors(NodeModel nodeModel, Execution execution) {
        List<FlwTaskActor> flwTaskActors = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(nodeModel.getNodeAssigneeList())) {
            final Integer actorType = this.getActorType(nodeModel);
            if (null != actorType) {
                for (NodeAssignee nodeAssignee : nodeModel.getNodeAssigneeList()) {
                    flwTaskActors.add(FlwTaskActor.of(nodeAssignee, actorType));
                }
            }
        }
        return ObjectUtils.isEmpty(flwTaskActors) ? null : flwTaskActors;
    }

    /**
     * 检查发起人权限
     * @param nodeModel 节点模型
     * @param flowCreator 流程创建者
     * @return 是否有权限
     */
    private boolean checkInitiatorPermission(NodeModel nodeModel, FlowCreator flowCreator) {
        List<NodeAssignee> nodeAssigneeList = nodeModel.getNodeAssigneeList();
        if (CollectionUtil.isEmpty(nodeAssigneeList)) {
            // 没有指定参与者，默认所有人都有权限
            return true;
        }

        // 1:指定成员; 3:角色;7:部门
        Integer setType = nodeModel.getSetType();
        if (setType == null) {
            // 没有设置setType，默认所有人都有权限
            return true;
        }

        String userId = flowCreator.getCreateId();
        SysUser currentUser = sysUserMapper.selectById(userId);
        if (currentUser == null) {
            log.error("用户不存在: {}", userId);
            return false;
        }

        // 获取用户角色ID列表
        List<String> userRoleIds = sysUserRoleMapper.selectList(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());

        // 获取用户部门ID
        String userDeptId = currentUser.getDeptId();

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
     * 检查角色权限
     */
    private boolean checkRolePermission(NodeModel nodeModel, FlowCreator flowCreator) {
        String userId = flowCreator.getCreateId();
        List<String> userRoleIds = sysUserRoleMapper.selectList(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());

        return nodeModel.getNodeAssigneeList().stream()
                .anyMatch(assignee -> userRoleIds.contains(assignee.getId()));
    }

    /**
     * 检查部门权限
     */
    private boolean checkDepartmentPermission(NodeModel nodeModel, FlowCreator flowCreator) {
        String userId = flowCreator.getCreateId();
        SysUser currentUser = sysUserMapper.selectById(userId);
        if (currentUser == null) {
            return false;
        }

        String userDeptId = currentUser.getDeptId();
        return nodeModel.getNodeAssigneeList().stream()
                .anyMatch(assignee -> Objects.equals(userDeptId, assignee.getId()));
    }

    /**
     * 获取参与者类型
     */
    public Integer getActorType(NodeModel nodeModel) {
        // 0，用户
        if (NodeSetType.specifyMembers.eq(nodeModel.getSetType())
                || NodeSetType.initiatorThemselves.eq(nodeModel.getSetType())
                || NodeSetType.initiatorSelected.eq(nodeModel.getSetType())) {
            return 0;
        }

        // 1，角色
        if (NodeSetType.role.eq(nodeModel.getSetType())) {
            return 1;
        }

        // 2，部门
        if (NodeSetType.department.eq(nodeModel.getSetType())) {
            return 2;
        }

        // 其它类型
        return 0;
    }
} 