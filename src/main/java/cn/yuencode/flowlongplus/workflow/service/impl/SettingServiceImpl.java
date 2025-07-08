package cn.yuencode.flowlongplus.workflow.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.yuencode.flowlongplus.config.exception.CommonException;
import cn.yuencode.flowlongplus.config.mybatis.ApiContext;
import cn.yuencode.flowlongplus.entity.SysUser;
import cn.yuencode.flowlongplus.entity.SysUserRole;
import cn.yuencode.flowlongplus.mapper.SysUserMapper;
import cn.yuencode.flowlongplus.mapper.SysUserRoleMapper;
import cn.yuencode.flowlongplus.util.R;
import cn.yuencode.flowlongplus.workflow.config.WorkflowProperties;
import cn.yuencode.flowlongplus.workflow.controller.req.ProcessTemplateReq;
import cn.yuencode.flowlongplus.workflow.controller.res.TemplateGroupRes;
import cn.yuencode.flowlongplus.workflow.dto.DeployDto;
import cn.yuencode.flowlongplus.workflow.dto.TemplateGroupDto;
import cn.yuencode.flowlongplus.workflow.entity.WflowProcessGroups;
import cn.yuencode.flowlongplus.workflow.entity.WflowProcessTemplates;
import cn.yuencode.flowlongplus.workflow.entity.WflowTemplateGroup;
import cn.yuencode.flowlongplus.workflow.mapper.WflowProcessTemplatesMapper;
import cn.yuencode.flowlongplus.workflow.mapper.WflowTemplateGroupMapper;
import cn.yuencode.flowlongplus.workflow.service.SettingService;
import cn.yuencode.flowlongplus.workflow.service.WflowProcessGroupsService;
import cn.yuencode.flowlongplus.workflow.service.WflowTemplateGroupService;
import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.model.ConditionNode;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.aizuda.bpm.engine.model.NodeModel;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 流程模版设置 服务实现类
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
public class SettingServiceImpl implements SettingService {
    private final FlowLongEngine flowLongEngine;
    private final WflowProcessTemplatesMapper wflowProcessTemplatesMapper;
    private final WflowTemplateGroupService wflowTemplateGroupService;
    private final WflowProcessGroupsService wflowProcessGroupsService;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final ApiContext apiContext;
    private final WflowTemplateGroupMapper wflowTemplateGroupMapper;
    private final WorkflowProperties workflowProperties;

    @Override
    public Object getTemplateGroups() {
        // 获取当前用户信息
        String loginId = (String) StpUtil.getLoginId();
        SysUser currentUser = sysUserMapper.selectById(loginId);
        if (currentUser == null) {
            log.error("用户不存在: {}", loginId);
            throw new CommonException("用户不存在");
        }

        // 获取用户角色ID列表
        List<String> userRoleIds = sysUserRoleMapper.selectList(
                        Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, loginId))
                .stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());

        // 获取用户部门ID
        String userDeptId = currentUser.getDeptId();

        List<TemplateGroupDto> templateGroupDtoList = wflowTemplateGroupMapper.getAllFormAndGroups();

        // 过滤有权限的模板
        List<TemplateGroupDto> filteredTemplates = templateGroupDtoList.stream()
                .filter(template -> {
                    if (template.getTemplateId() == null) {
                        // 分组标题行，保留
                        return true;
                    }

                    // 获取模板的流程定义
                    WflowProcessTemplates processTemplate = wflowProcessTemplatesMapper.selectById(template.getTemplateId());
                    if (processTemplate == null || processTemplate.getProcess() == null) {
                        return false;
                    }

                    // 解析流程定义，获取发起人节点配置
                    try {
                        NodeModel nodeModel = JSONObject.parseObject(processTemplate.getProcess(), NodeModel.class);
                        return hasInitiatorPermission(nodeModel, loginId, userRoleIds, userDeptId);
                    } catch (Exception e) {
                        log.error("解析流程定义失败，templateId: {}", template.getTemplateId(), e);
                        return false;
                    }
                })
                .collect(Collectors.toList());

        Map<String, List<TemplateGroupDto>> coverMap = new LinkedHashMap<>();
        filteredTemplates.forEach(fg -> {
            List<TemplateGroupDto> bos = coverMap.get(fg.getGroupId());
            if (CollectionUtil.isEmpty(bos)) {
                List<TemplateGroupDto> list = new ArrayList<>();
                list.add(fg);
                coverMap.put(fg.getGroupId(), list);
            } else {
                bos.add(fg);
            }
        });
        List<TemplateGroupRes> results = new ArrayList<>();
        coverMap.forEach((key, val) -> {
            List<TemplateGroupRes.Template> templates = new ArrayList<>();
            val.forEach(v -> {
                if (ObjectUtil.isNotNull(v.getTemplateId())) {
                    templates.add(TemplateGroupRes.Template.builder().templateId(v.getTemplateId()).templateName(v.getTemplateName()).tgId(v.getId()).remark(v.getRemark()).logo(v.getLogo()).status(v.getStatus()).updateTime(DateFormatUtils.format(v.getUpdateTime(), "yyyy年MM月dd日 HH时mm分ss秒")).templateId(v.getTemplateId()).build());
                }
            });
            results.add(TemplateGroupRes.builder().id(key).name(val.get(0).getGroupName()).items(templates).build());
        });
        return R.success(results);
    }

    @Override
    @Transactional
    public Object templateGroupsSort(List<TemplateGroupRes> groups) {
        int i = 0, j = 0;
        try {
            for (TemplateGroupRes group : groups) {
                wflowProcessGroupsService.updateById(WflowProcessGroups.builder().groupId(group.getId()).sortNum(group.getId().equals(0L) ? 999999 : i + 2).build());
                for (TemplateGroupRes.Template item : group.getItems()) {
                    wflowTemplateGroupService.updateById(WflowTemplateGroup.builder().id(item.getTgId()).groupId(group.getId()).templateId(item.getTemplateId()).sortNum(j + 1).build());
                    j++;
                }
                i++;
                j = 0;
            }
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return R.fail("排序异常 " + e.getMessage());
        }
        return R.success("排序成功");
    }

    @Override
    public Object updateTemplateGroupName(String id, String name) {
        if ("1".equals(id)) {
            return R.fail("基础分组不允许修改");
        }
        wflowProcessGroupsService.updateById(WflowProcessGroups.builder().groupId(id).groupName(name.trim()).build());
        return R.success("修改成功");
    }

    @Override
    public Object createTemplateGroup(String name) {
        LambdaQueryWrapper<WflowProcessGroups> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(WflowProcessGroups::getGroupName, name);
        if (wflowProcessGroupsService.count(lambdaQueryWrapper) > 0) {
            return R.fail("分组名称 [" + name + "] 已存在");
        }
        Date date = new Date();
        WflowProcessGroups wflowProcessGroups = WflowProcessGroups.builder().groupName(name).sortNum(1).created(date).updated(date).build();
        wflowProcessGroupsService.save(wflowProcessGroups);
        return R.success("添加分组 " + name + " 成功");
    }

    @Override
    @Transactional
    public Object deleteTemplateGroup(Long id) {
        if (id < 2) {
            return R.fail("基础分组不允许删除");
        }
        LambdaUpdateWrapper<WflowTemplateGroup> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper.set(WflowTemplateGroup::getGroupId, 1);
        lambdaUpdateWrapper.eq(WflowTemplateGroup::getGroupId, id);
        wflowTemplateGroupService.update(lambdaUpdateWrapper);
        wflowProcessGroupsService.removeById(id);
        return R.success("删除分组成功");
    }


    @Override
    public Object getGroupOptions() {
        List<WflowProcessGroups> list = wflowProcessGroupsService.list(new LambdaQueryWrapper<WflowProcessGroups>().orderByAsc(WflowProcessGroups::getSortNum));
        return list.stream().map(item -> {
            JSONObject object = new JSONObject();
            object.put("label", item.getGroupName());
            object.put("value", item.getGroupId());
            return object;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTemplate(ProcessTemplateReq processTemplateReq) {

        List<WflowProcessTemplates> keys = wflowProcessTemplatesMapper.selectList(Wrappers.<WflowProcessTemplates>lambdaQuery().eq(WflowProcessTemplates::getTemplateKey, processTemplateReq.getTemplateKey()));
        if (!keys.isEmpty()) {
            log.error("标识重复:{}", processTemplateReq.getTemplateKey());
            throw new CommonException("标识重复");
        }
        String groupId = processTemplateReq.getGroupId();

        WflowProcessGroups group = wflowProcessGroupsService.getById(groupId);
        if (null == group) {
            log.error("分组:{} 不存在", groupId);
            throw new CommonException("非法数据");
        }

        String loginId = (String) StpUtil.getLoginId();
        SysUser sysUser = sysUserMapper.selectById(loginId);
        if (null == sysUser) {
            log.error("用户:{} 不存在", loginId);
            throw new CommonException("非法用户");
        }
        FlowCreator creator = FlowCreator.of(String.valueOf(apiContext.getCurrentTenantId()), loginId, sysUser.getNickname());
        DeployDto deployDTO = new DeployDto();
        deployDTO.setKey(processTemplateReq.getTemplateKey());
        deployDTO.setName(processTemplateReq.getTemplateName());
        deployDTO.setInstanceUrl("");
        NodeModel nodeModel = JSONObject.parseObject(processTemplateReq.getProcess(), NodeModel.class);
        // 适配新版本，确保有发起人节点
        if (!Objects.equals(nodeModel.getType(), 0)) {
            nodeModel.setType(0);
        }
        deployDTO.setNodeConfig(nodeModel);

        String jsonString = JSONObject.toJSONString(deployDTO, SerializerFeature.WriteMapNullValue);
        Long deployId = flowLongEngine.processService().deploy(new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)), creator, false);
        FlwProcess flwProcess = flowLongEngine.processService().getProcessById(deployId);
        WflowProcessTemplates processTemplate = new WflowProcessTemplates();
        processTemplate.setWorkflowId(deployId.toString());
        processTemplate.setWorkflowVersion(flwProcess.getProcessVersion());
        processTemplate.setTemplateName(processTemplateReq.getTemplateName());
        processTemplate.setTemplateKey(processTemplateReq.getTemplateKey());
        processTemplate.setGroupId(groupId);
        processTemplate.setProcess(processTemplateReq.getProcess());
        processTemplate.setFormItems(processTemplateReq.getFormItems());
        processTemplate.setSettings(processTemplateReq.getSettings());
        processTemplate.setRemark(processTemplateReq.getRemark());
        wflowProcessTemplatesMapper.insert(processTemplate);

        WflowTemplateGroup wflowTemplateGroup = new WflowTemplateGroup();
        wflowTemplateGroup.setTemplateId(processTemplate.getTemplateId());
        wflowTemplateGroup.setGroupId(groupId);
        wflowTemplateGroupService.save(wflowTemplateGroup);
    }

    @Override
    public Object getTemplateDetail(String templateId) {
        WflowProcessTemplates temp = wflowProcessTemplatesMapper.selectById(templateId);
        WflowProcessTemplates processTemplate = wflowProcessTemplatesMapper.selectOne(Wrappers.<WflowProcessTemplates>lambdaQuery().eq(WflowProcessTemplates::getTemplateKey, temp.getTemplateKey()).apply("(template_key, template_version) in(" + "SELECT template_key, MAX(template_version) " + "FROM wflow_process_templates " + "GROUP BY template_key)"));
        if (null == processTemplate) {
            throw new CommonException("非法数据");
        }
        return processTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(ProcessTemplateReq processTemplateReq) {
        processTemplateReq.setTemplateKey(null);
        String templateId = processTemplateReq.getTemplateId();
        WflowProcessTemplates temp = wflowProcessTemplatesMapper.selectById(templateId);
        WflowProcessTemplates template = wflowProcessTemplatesMapper.selectOne(Wrappers.<WflowProcessTemplates>lambdaQuery()
                .eq(WflowProcessTemplates::getTemplateKey, temp.getTemplateKey())
                .apply("(template_key, template_version) in("
                        + "SELECT template_key, MAX(template_version) "
                        + "FROM wflow_process_templates " + "GROUP BY template_key)"));
        if (null == template) {
            log.error("模版不存在 templateId:{}", processTemplateReq.getTemplateId());
            throw new CommonException("非法数据");
        }
        if (!Objects.equals(processTemplateReq.getTemplateVersion(), template.getTemplateVersion())) {
            log.error("版本不存在 templateId:{} version:{}", processTemplateReq.getTemplateId(), processTemplateReq.getTemplateVersion());
            throw new CommonException("非法数据");
        }

        String groupId = processTemplateReq.getGroupId();

        WflowProcessGroups group1 = wflowProcessGroupsService.getById(groupId);
        if (null == group1) {
            log.error("分组:{} 不存在", groupId);
            throw new CommonException("非法数据");
        }

        // 部署流程
        String loginId = (String) StpUtil.getLoginId();
        SysUser sysUser = sysUserMapper.selectById(loginId);
        if (null == sysUser) {
            log.error("用户 {} 不存在", loginId);
            throw new CommonException("非法用户");
        }
        FlowCreator creator = FlowCreator.of(apiContext.getCurrentTenantId(), loginId, sysUser.getNickname());

        DeployDto deployDTO = new DeployDto();
        deployDTO.setKey(template.getTemplateKey());
        deployDTO.setName(processTemplateReq.getTemplateName());
        deployDTO.setInstanceUrl("");
        NodeModel nodeModel = JSONObject.parseObject(processTemplateReq.getProcess(), NodeModel.class);
        // 适配新版本，确保有发起人节点
        if (!Objects.equals(nodeModel.getType(), 0)) {
            nodeModel.setType(0);
        }
        deployDTO.setNodeConfig(nodeModel);

        String jsonString = JSONObject.toJSONString(deployDTO, SerializerFeature.WriteMapNullValue);
        // flowlong bug: repeat为false不会更新流程定义
        Long deployId = flowLongEngine.processService().deploy(new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)), creator, true);
        FlwProcess flwProcess = flowLongEngine.processService().getProcessById(deployId);
        // 更新模板, version+1
        WflowProcessTemplates processTemplate = new WflowProcessTemplates();
        processTemplate.setWorkflowId(deployId.toString());
        processTemplate.setWorkflowVersion(flwProcess.getProcessVersion());
        processTemplate.setTemplateName(processTemplateReq.getTemplateName());
        processTemplate.setTemplateKey(template.getTemplateKey());
        processTemplate.setGroupId(processTemplateReq.getGroupId());
        processTemplate.setProcess(processTemplateReq.getProcess());
        processTemplate.setFormItems(processTemplateReq.getFormItems());
        processTemplate.setSettings(processTemplateReq.getSettings());
        processTemplate.setRemark(processTemplateReq.getRemark());
        processTemplate.setTemplateVersion(template.getTemplateVersion() + 1);
        wflowProcessTemplatesMapper.insert(processTemplate);

        // 更新分组
        WflowTemplateGroup group = wflowTemplateGroupService.getOne(Wrappers.<WflowTemplateGroup>lambdaQuery().eq(WflowTemplateGroup::getGroupId, template.getGroupId()).eq(WflowTemplateGroup::getTemplateId, templateId));
        if (null == group) {
            throw new CommonException("非法数据");
        }
        group.setGroupId(processTemplateReq.getGroupId());
        group.setTemplateId(processTemplate.getTemplateId());
        wflowTemplateGroupService.updateById(group);
    }

    /**
     * 检查用户是否有发起流程的权限
     *
     * @param nodeModel 流程节点模型
     * @param userId 用户ID
     * @param userRoleIds 用户角色ID列表
     * @param userDeptId 用户部门ID
     * @return true 有权限，false 无权限
     */
    private boolean hasInitiatorPermission(NodeModel nodeModel, String userId, List<String> userRoleIds, String userDeptId) {
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
