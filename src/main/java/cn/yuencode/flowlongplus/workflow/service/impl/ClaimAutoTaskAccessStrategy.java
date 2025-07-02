package cn.yuencode.flowlongplus.workflow.service.impl;

import cn.yuencode.flowlongplus.entity.SysUser;
import cn.yuencode.flowlongplus.entity.SysUserRole;
import cn.yuencode.flowlongplus.mapper.SysUserMapper;
import cn.yuencode.flowlongplus.mapper.SysUserRoleMapper;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.impl.GeneralAccessStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * 自动认领任务-访问策略类
 * 用于处理角色和部门类型的任务认领
 */
@Slf4j
@Component
public class ClaimAutoTaskAccessStrategy extends GeneralAccessStrategy {

    @Resource
    private SysUserMapper sysUserMapper;
    
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    /**
     * 重写isAllowed方法，用于处理角色和部门类型的任务认领
     * @param userId     用户ID
     * @param taskActors 参与者列表
     * @return
     */
    @Override
    public FlwTaskActor isAllowed(String userId, List<FlwTaskActor> taskActors) {
        if (taskActors == null || taskActors.isEmpty()) {
            return null;
        }

        // 检查是否有角色或部门类型的任务参与者
        boolean hasRoleOrDeptTask = taskActors.stream()
                .anyMatch(t -> Objects.equals(1, t.getActorType()) || Objects.equals(2, t.getActorType()));

        if (hasRoleOrDeptTask) {
            // 获取当前用户信息
            SysUser currentUser = sysUserMapper.selectById(userId);
            if (currentUser == null) {
                log.warn("用户不存在: {}", userId);
                return null;
            }

            // 检查用户是否匹配任何角色或部门任务，正常情况下只会有一个匹配的参与者
            for (FlwTaskActor taskActor : taskActors) {
                if (Objects.equals(1, taskActor.getActorType())) {
                    // 角色类型：检查用户是否属于该角色
                    if (isUserInRole(userId, taskActor.getActorId())) {
                        log.info("用户 {} 匹配角色任务 {}", userId, taskActor.getActorId());
                        return taskActor;
                    }
                } else if (Objects.equals(2, taskActor.getActorType())) {
                    // 部门类型：检查用户是否属于该部门
                    if (Objects.equals(currentUser.getDeptId(), taskActor.getActorId())) {
                        log.info("用户 {} 匹配部门任务 {}", userId, taskActor.getActorId());
                        return taskActor;
                    }
                } else if (Objects.equals(0, taskActor.getActorType())) {
                    // 用户类型：直接匹配用户ID
                    if (Objects.equals(userId, taskActor.getActorId())) {
                        log.info("用户 {} 匹配用户任务 {}", userId, taskActor.getActorId());
                        return taskActor;
                    }
                }
            }
            log.warn("用户 {} 不匹配任何任务参与者", userId);
            return null;
        }

        // 如果没有角色或部门任务，使用默认逻辑
        return super.isAllowed(userId, taskActors);
    }

    /**
     * 检查用户是否属于指定角色
     */
    private boolean isUserInRole(String userId, String roleId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, roleId)
        );
        return !userRoles.isEmpty();
    }
}
