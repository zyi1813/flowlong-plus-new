package cn.yuencode.flowlongplus.service;

import cn.yuencode.flowlongplus.controller.req.SysRolePageParamReq;
import cn.yuencode.flowlongplus.controller.req.SysRoleReq;
import cn.yuencode.flowlongplus.entity.SysRole;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 角色 服务类
 *
 * <p>
 * 尊重知识产权，不允许非法使用，后果自负
 * </p>
 *
 * @author 贾小宇
 * @since 1.0
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 获取角色分页列表
     *
     * @param params 查询参数
     * @return 分页数据
     */
    Object getList(SysRolePageParamReq params);

    /**
     * 获取角色详情
     *
     * @param roleId 角色id
     * @return 角色信息
     */
    Object getInfo(String roleId);

    /**
     * 新增角色
     *
     * @param roleReq 角色数据
     */
    void addRole(SysRoleReq roleReq);

    /**
     * 更新角色信息
     *
     * @param roleReq 角色数据
     */
    void updateRole(SysRoleReq roleReq);

    /**
     * 删除角色
     *
     * @param ids 角色id
     */
    void deleteRole(String[] ids);

    /**
     * 获取角色下拉选项
     *
     * @return 选项数据
     */
    Object options();

    /**
     * 获取所有角色列表（用于流程审批人选择）
     *
     * @return 角色列表
     */
    Object getAllRoles();

    /**
     * 根据角色ID获取用户列表
     *
     * @param roleId 角色ID
     * @return 用户列表
     */
    Object getUsersByRole(String roleId);
}
