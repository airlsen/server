package com.daxiang.service;

import com.daxiang.mbg.po.TestPlan;
import com.daxiang.mbg.po.TestPlanExample;
import com.daxiang.mbg.po.User;
import com.daxiang.model.Page;
import com.daxiang.model.PageRequest;
import com.daxiang.dao.TestPlanDao;
import com.daxiang.model.vo.TestPlanVo;
import com.daxiang.security.SecurityUtil;
import com.github.pagehelper.PageHelper;
import com.daxiang.mbg.mapper.TestPlanMapper;
import com.daxiang.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * Created by jiangyitao.
 */
@Slf4j
@Service
public class TestPlanService {

    private static final ThreadPoolTaskScheduler TASK_SCHEDULER;
    private static final Map<Integer, ScheduledFuture> TEST_PLAN_SCHEDULED_FUTURE_MAP = new ConcurrentHashMap<>();

    static {
        TASK_SCHEDULER = new ThreadPoolTaskScheduler();
        TASK_SCHEDULER.initialize();
    }

    @Autowired
    private TestPlanMapper testPlanMapper;
    @Autowired
    private TestPlanDao testPlanDao;
    @Autowired
    private TestTaskService testTaskService;
    @Autowired
    private UserService userService;

    @Transactional
    public Response add(TestPlan testPlan) {
        if (testPlan.getEnableSchedule() == TestPlan.ENABLE_SCHEDULE && StringUtils.isEmpty(testPlan.getCronExpression())) {
            // 开启定时任务，表达式不能为空
            return Response.fail("cron表达式不能为空");
        }

        testPlan.setCreateTime(new Date());
        testPlan.setCreatorUid(SecurityUtil.getCurrentUserId());

        int insertRow;
        try {
            insertRow = testPlanMapper.insertSelective(testPlan);
        } catch (DuplicateKeyException e) {
            return Response.fail("重复命名");
        }

        if (testPlan.getEnableSchedule() == TestPlan.ENABLE_SCHEDULE) {
            addOrUpdateScheduleTask(testPlan);
        }

        return insertRow == 1 ? Response.success("添加TestPlan成功") : Response.fail("添加TestPlan失败，请稍后重试");
    }

    public Response delete(Integer testPlanId) {
        if (testPlanId == null) {
            return Response.fail("测试计划id不能为空");
        }

        cancelScheduleTask(testPlanId);

        int deleteRow = testPlanMapper.deleteByPrimaryKey(testPlanId);
        return deleteRow == 1 ? Response.success("删除TestPlan成功") : Response.fail("删除TestPlan失败，请稍后重试");
    }

    @Transactional
    public Response update(TestPlan testPlan) {
        if (testPlan.getEnableSchedule() == TestPlan.ENABLE_SCHEDULE && StringUtils.isEmpty(testPlan.getCronExpression())) {
            // 开启定时任务，表达式不能为空
            return Response.fail("cron表达式不能为空");
        }

        int updateRow;
        try {
            updateRow = testPlanMapper.updateByPrimaryKeyWithBLOBs(testPlan);
        } catch (DuplicateKeyException e) {
            return Response.fail("命名冲突");
        }

        if (testPlan.getEnableSchedule() == TestPlan.ENABLE_SCHEDULE) {
            addOrUpdateScheduleTask(testPlan);
        } else {
            cancelScheduleTask(testPlan.getId());
        }

        return updateRow == 1 ? Response.success("更新TestPlan成功") : Response.fail("更新TestPlan失败，请稍后重试");
    }

    public Response list(TestPlan testPlan, PageRequest pageRequest) {
        boolean needPaging = pageRequest.needPaging();
        if (needPaging) {
            PageHelper.startPage(pageRequest.getPageNum(), pageRequest.getPageSize());
        }

        List<TestPlan> testPlans = selectByTestPlan(testPlan);
        List<TestPlanVo> testPlanVos = convertTestPlansToTestPlanVos(testPlans);

        if (needPaging) {
            long total = Page.getTotal(testPlans);
            return Response.success(Page.build(testPlanVos, total));
        } else {
            return Response.success(testPlanVos);
        }
    }

    private List<TestPlanVo> convertTestPlansToTestPlanVos(List<TestPlan> testPlans) {
        if (CollectionUtils.isEmpty(testPlans)) {
            return new ArrayList<>();
        }

        List<Integer> creatorUids = testPlans.stream()
                .map(TestPlan::getCreatorUid)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, User> userMap = userService.getUserMapByIds(creatorUids);

        return testPlans.stream().map(testPlan -> {
            TestPlanVo testPlanVo = new TestPlanVo();
            BeanUtils.copyProperties(testPlan, testPlanVo);

            if (testPlan.getCreatorUid() != null) {
                User user = userMap.get(testPlan.getCreatorUid());
                if (user != null) {
                    testPlanVo.setCreatorNickName(user.getNickName());
                }
            }

            return testPlanVo;
        }).collect(Collectors.toList());
    }

    private List<TestPlan> selectByTestPlan(TestPlan testPlan) {
        TestPlanExample example = new TestPlanExample();
        TestPlanExample.Criteria criteria = example.createCriteria();

        if (testPlan != null) {
            if (testPlan.getId() != null) {
                criteria.andIdEqualTo(testPlan.getId());
            }
            if (testPlan.getProjectId() != null) {
                criteria.andProjectIdEqualTo(testPlan.getProjectId());
            }
            if (!StringUtils.isEmpty(testPlan.getName())) {
                criteria.andNameEqualTo(testPlan.getName());
            }
            if (testPlan.getRunMode() != null) {
                criteria.andRunModeEqualTo(testPlan.getRunMode());
            }
            if (testPlan.getEnableSchedule() != null) {
                criteria.andEnableScheduleEqualTo(testPlan.getEnableSchedule());
            }
            if (testPlan.getEnvironmentId() != null) {
                criteria.andEnvironmentIdEqualTo(testPlan.getEnvironmentId());
            }
            if (testPlan.getEnableRecordVideo() != null) {
                criteria.andEnableRecordVideoEqualTo(testPlan.getEnableRecordVideo());
            }
        }
        example.setOrderByClause("create_time desc");

        return testPlanMapper.selectByExampleWithBLOBs(example);
    }

    /**
     * 首次启动server，按计划去执行所有开启的定时任务
     */
    public void scheduleEnabledTasks() {
        TestPlan query = new TestPlan();
        query.setEnableSchedule(TestPlan.ENABLE_SCHEDULE);

        List<TestPlan> testPlans = selectByTestPlan(query);
        testPlans.forEach(testPlan -> addOrUpdateScheduleTask(testPlan));
    }

    /**
     * 添加或更新定时任务
     */
    private synchronized void addOrUpdateScheduleTask(TestPlan testPlan) {
        ScheduledFuture future = TEST_PLAN_SCHEDULED_FUTURE_MAP.get(testPlan.getId());
        if (future != null) {
            // 取消上一次设置的定时任务
            log.info("cancel schedule, testPlan: {}", testPlan.getName());
            future.cancel(true);
        }
        log.info("add schedule, testPlan: {}", testPlan.getName());
        future = TASK_SCHEDULER.schedule(() -> testTaskService.commit(testPlan.getId(), testPlan.getCreatorUid()), new CronTrigger(testPlan.getCronExpression()));
        TEST_PLAN_SCHEDULED_FUTURE_MAP.put(testPlan.getId(), future);
    }

    /**
     * 取消定时任务
     *
     * @param testPlanId
     */
    private synchronized void cancelScheduleTask(Integer testPlanId) {
        ScheduledFuture future = TEST_PLAN_SCHEDULED_FUTURE_MAP.get(testPlanId);
        if (future != null) {
            log.info("cancel schedule, testPlanId: {}", testPlanId);
            future.cancel(true);
            TEST_PLAN_SCHEDULED_FUTURE_MAP.remove(testPlanId);
        }
    }

    public TestPlan getTestPlanById(Integer testPlanId) {
        return testPlanMapper.selectByPrimaryKey(testPlanId);
    }

    public List<TestPlan> getTestPlansByTestSuiteId(Integer testSuiteId) {
        return testPlanDao.selectByTestSuiteId(testSuiteId);
    }

    public List<TestPlan> getTestPlansByActionId(Integer actionId) {
        return testPlanDao.selectByActionId(actionId);
    }

    public List<TestPlan> getTestPlansByEnvironmentId(Integer envId) {
        TestPlan query = new TestPlan();
        query.setEnvironmentId(envId);
        return selectByTestPlan(query);
    }
}