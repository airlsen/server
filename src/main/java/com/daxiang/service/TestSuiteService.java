package com.daxiang.service;

import com.daxiang.dao.TestSuiteDao;
import com.daxiang.mbg.po.*;
import com.daxiang.model.PageRequest;
import com.daxiang.model.Response;
import com.daxiang.security.SecurityUtil;
import com.github.pagehelper.PageHelper;
import com.daxiang.mbg.mapper.TestSuiteMapper;
import com.daxiang.model.Page;
import com.daxiang.model.vo.TestSuiteVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by jiangyitao.
 */
@Service
public class TestSuiteService {

    @Autowired
    private TestSuiteDao testSuiteDao;
    @Autowired
    private TestSuiteMapper testSuiteMapper;
    @Autowired
    private TestPlanService testPlanService;
    @Autowired
    private UserService userService;

    public Response add(TestSuite testSuite) {
        testSuite.setCreateTime(new Date());
        testSuite.setCreatorUid(SecurityUtil.getCurrentUserId());

        int insertRow;
        try {
            insertRow = testSuiteMapper.insertSelective(testSuite);
        } catch (DuplicateKeyException e) {
            return Response.fail("命名冲突");
        }
        return insertRow == 1 ? Response.success("添加TestSuite成功") : Response.fail("添加TestSuite失败");
    }

    public Response delete(Integer testSuiteId) {
        if (testSuiteId == null) {
            return Response.fail("testSuiteId不能为空");
        }

        // 检查该测试集是否被testplan使用
        List<TestPlan> testPlans = testPlanService.getTestPlansByTestSuiteId(testSuiteId);
        if (!CollectionUtils.isEmpty(testPlans)) {
            String testPlanNames = testPlans.stream().map(TestPlan::getName).collect(Collectors.joining("、"));
            return Response.fail("测试计划: " + testPlanNames + "，正在使用，无法删除");
        }

        int deleteRow = testSuiteMapper.deleteByPrimaryKey(testSuiteId);
        return deleteRow == 1 ? Response.success("删除TestSuite成功") : Response.fail("删除TestSuite失败，请稍后重试");
    }

    public Response update(TestSuite testSuite) {
        int updateRow;
        try {
            updateRow = testSuiteMapper.updateByPrimaryKeySelective(testSuite);
        } catch (DuplicateKeyException e) {
            return Response.fail("命名冲突");
        }
        return updateRow == 1 ? Response.success("更新TestSuite成功") : Response.fail("更新TestSuite失败");
    }

    public Response list(TestSuite testSuite, PageRequest pageRequest) {
        boolean needPaging = pageRequest.needPaging();
        if (needPaging) {
            PageHelper.startPage(pageRequest.getPageNum(), pageRequest.getPageSize());
        }

        List<TestSuite> testSuites = selectByTestSuite(testSuite);
        List<TestSuiteVo> testSuiteVos = convertTestSuitesToTestSuiteVos(testSuites);

        if (needPaging) {
            long total = Page.getTotal(testSuites);
            return Response.success(Page.build(testSuiteVos, total));
        } else {
            return Response.success(testSuiteVos);
        }
    }

    private List<TestSuiteVo> convertTestSuitesToTestSuiteVos(List<TestSuite> testSuites) {
        if (CollectionUtils.isEmpty(testSuites)) {
            return new ArrayList<>();
        }

        List<Integer> creatorUids = testSuites.stream()
                .map(TestSuite::getCreatorUid)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, User> userMap = userService.getUserMapByIds(creatorUids);

        return testSuites.stream().map(testSuite -> {
            TestSuiteVo testSuiteVo = new TestSuiteVo();
            BeanUtils.copyProperties(testSuite, testSuiteVo);

            if (testSuite.getCreatorUid() != null) {
                User user = userMap.get(testSuite.getCreatorUid());
                if (user != null) {
                    testSuiteVo.setCreatorNickName(user.getNickName());
                }
            }

            return testSuiteVo;
        }).collect(Collectors.toList());
    }

    private List<TestSuite> selectByTestSuite(TestSuite testSuite) {
        TestSuiteExample example = new TestSuiteExample();
        TestSuiteExample.Criteria criteria = example.createCriteria();

        if (testSuite != null) {
            if (testSuite.getId() != null) {
                criteria.andIdEqualTo(testSuite.getId());
            }
            if (testSuite.getProjectId() != null) {
                criteria.andProjectIdEqualTo(testSuite.getProjectId());
            }
            if (!StringUtils.isEmpty(testSuite.getName())) {
                criteria.andNameEqualTo(testSuite.getName());
            }
        }
        example.setOrderByClause("create_time desc");

        return testSuiteMapper.selectByExampleWithBLOBs(example);
    }

    public List<TestSuite> getTestSuitesByIds(List<Integer> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return new ArrayList<>();
        }

        TestSuiteExample example = new TestSuiteExample();
        TestSuiteExample.Criteria criteria = example.createCriteria();

        criteria.andIdIn(ids);
        return testSuiteMapper.selectByExampleWithBLOBs(example);
    }

    public List<TestSuite> getTestSuitesByActionId(Integer actionId) {
        return testSuiteDao.selectByActionId(actionId);
    }

}
