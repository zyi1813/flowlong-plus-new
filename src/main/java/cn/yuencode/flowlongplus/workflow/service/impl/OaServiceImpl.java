package cn.yuencode.flowlongplus.workflow.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.yuencode.flowlongplus.entity.SysDept;
import cn.yuencode.flowlongplus.entity.SysUser;
import cn.yuencode.flowlongplus.entity.SysRole;
import cn.yuencode.flowlongplus.service.SysUserService;
import cn.yuencode.flowlongplus.service.SysRoleService;
import cn.yuencode.flowlongplus.service.impl.SysDeptServiceImpl;
import cn.yuencode.flowlongplus.workflow.dto.OrgTreeDto;
import cn.yuencode.flowlongplus.workflow.service.OaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * 组织部门 服务实现类
 *
 * <p>
 * 尊重知识产权，不允许非法使用，后果自负
 * </p>
 *
 * @author 贾小宇
 * @since 1.0
 */
@Service
public class OaServiceImpl implements OaService {

    @Resource
    private SysDeptServiceImpl departmentsService;

    @Resource
    private SysUserService sysUserService;

    @Resource
    private SysRoleService sysRoleService;

    /**
     * 查询组织架构树
     *
     * @param deptId 部门id
     * @return 组织架构树数据
     */
    @Override
    public Object getOrgTreeData(String deptId) {
        // 查询所有的用户
        LambdaQueryWrapper<SysUser> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        List<SysUser> sysUserList = sysUserService.list(lambdaQueryWrapper);

        List<OrgTreeDto> orgTreeDtoList = new LinkedList<>();
        if (StringUtils.isNotBlank(deptId)) {
            LambdaQueryWrapper<SysDept> departmentsLambdaQueryWrapper = new LambdaQueryWrapper<>();
            departmentsLambdaQueryWrapper.eq(SysDept::getParent, deptId);
            List<SysDept> sysDeptList = departmentsService.list(departmentsLambdaQueryWrapper);
            sysDeptList.forEach(dept -> {
                orgTreeDtoList.add(OrgTreeDto.builder()
                        .id(dept.getDeptId())
                        .name(dept.getDeptName())
                        .selected(false)
                        .type("dept")
                        .build());
            });
            // 根据用户表的dept_id 过滤收集用户
            sysUserList.forEach(user -> {
                if (ObjectUtils.equals(user.getDeptId(), deptId)) {
                    orgTreeDtoList.add(OrgTreeDto.builder()
                            .id(user.getId())
                            .name(user.getNickname())
                            .nickname(user.getNickname())
                            .username(user.getAccount())
                            .avatar("")
                            .sex(user.getSex())
                            .type("user")
                            .tenantId(String.valueOf(user.getTenantId()))
                            .selected(false)
                            .build());
                }
            });
            return orgTreeDtoList;
        }else {
            // 没有选部门，则查询全部用户
            sysUserList.forEach(user -> {
                orgTreeDtoList.add(OrgTreeDto.builder()
                        .id(user.getId())
                        .name(user.getNickname())
                        .nickname(user.getNickname())
                        .username(user.getAccount())
                        .avatar("")
                        .sex(user.getSex())
                        .type("user")
                        .tenantId(String.valueOf(user.getTenantId()))
                        .selected(false)
                        .build());
            });
            return orgTreeDtoList;
        }
    }

    /**
     * 模糊搜索用户
     *
     * @param username 用户名
     * @return 匹配到的用户
     */
    @Override
    public Object getOrgTreeUser(String username) {
        LambdaQueryWrapper<SysUser> lambdaQueryWrapper = new LambdaQueryWrapper<>();

        lambdaQueryWrapper.like(SysUser::getAccount, username).or().like(SysUser::getNickname, username);

        List<OrgTreeDto> list = new LinkedList<>();
        sysUserService.list(lambdaQueryWrapper).forEach(user -> {
            list.add(OrgTreeDto.builder().type("user")
                    .sex(user.getSex()).avatar("")
                    .name(user.getAccount() + "-" + user.getNickname()).id(user.getId())
                    .selected(false).build());
        });
        return list;
    }

    /**
     * 查询用户信息
     *
     * @param id 用户id
     * @return 用户信息
     */
    @Override
    public Object getUserInfo(String id, Integer setType) {
        switch (setType) {
            case 1: // 指定成员
            case 2: // 主管
            case 4: // 发起人自选
            case 5: // 发起人自己
            case 6: // 连续多级主管
            case 8: // 指定候选人
                try {
                    SysUser user = sysUserService.getById(Long.valueOf(id));
                    if (user != null) {
                        Map<String, Object> userInfo = new HashMap<>();
                        userInfo.put("id", user.getId());
                        userInfo.put("avatar", "");
                        userInfo.put("name", ObjectUtil.isNull(user.getNickname()) ? "" : user.getNickname());
                        userInfo.put("mobile", user.getMobile());
                        userInfo.put("type", "user");
                        return userInfo;
                    }
                } catch (Exception ignore) {}
                break;
            case 3: // 角色
                SysRole role = sysRoleService.getById(id);
                if (role != null) {
                    Map<String, Object> roleInfo = new HashMap<>();
                    roleInfo.put("id", role.getId());
                    roleInfo.put("name", role.getRoleName());
                    roleInfo.put("type", "role");
                    return roleInfo;
                }
                break;
            case 7: // 部门
                SysDept dept = departmentsService.getById(id);
                if (dept != null) {
                    Map<String, Object> deptInfo = new HashMap<>();
                    deptInfo.put("id", dept.getDeptId());
                    deptInfo.put("name", dept.getDeptName());
                    deptInfo.put("type", "dept");
                    return deptInfo;
                }
                break;
            default:
                break;
        }
        return null;
    }
}
