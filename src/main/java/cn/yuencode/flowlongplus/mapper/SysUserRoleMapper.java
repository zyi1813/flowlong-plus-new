package cn.yuencode.flowlongplus.mapper;

import cn.yuencode.flowlongplus.entity.SysUserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户角色关联 Mapper 接口
 * </p>
 *
 * @author jiaxiaoyu
 * @since 2024-05-31
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 根据角色ID查询用户列表
     *
     * @param roleId 角色ID
     * @return 用户列表
     */
    @Select("SELECT u.id, u.nickname FROM sys_user u " +
            "INNER JOIN sys_user_role ur ON u.id = ur.user_id " +
            "WHERE ur.role_id = #{roleId} AND u.is_enable = 1 " +
            "ORDER BY u.create_time")
    List<Map<String, Object>> selectUsersByRoleId(@Param("roleId") String roleId);
}
