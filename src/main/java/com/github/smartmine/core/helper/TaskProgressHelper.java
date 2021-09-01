package com.github.smartmine.core.helper;

import com.github.smartmine.core.conf.TaskConf;
import com.github.smartmine.core.task.NoticeTask;
import com.github.smartmine.core.type.TaskStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskProgressHelper {

    @Autowired
    private TaskConf taskConf;

    //当前任务状态
    private TaskStats taskStats = TaskStats.Normal;

    //记录时间
    private long recordTime = System.currentTimeMillis();


    public synchronized void updateState(int value, NoticeTask task) {
        //判断状态
        if (value >= 0 && value <= taskConf.getMinIncomeGpu()) {
            changeStat(TaskStats.Normal);
        } else if (value < 0) {
            changeStat(TaskStats.Sub);
        } else if (value > taskConf.getMinIncomeGpu()) {
            changeStat(TaskStats.Add);
        }


        //触发任务
        trigger( task);

    }

    private void trigger( NoticeTask task) {
        //状态时间
        long time = (System.currentTimeMillis() - this.recordTime) / 1000;
        if ((this.taskStats == TaskStats.Add && time >= taskConf.getKeepAddTime()) || (this.taskStats == TaskStats.Sub && time >= taskConf.getKeepSubTime())) {
            task.handle();
        }
    }


    /**
     * 改变状态
     *
     * @param taskStats
     */
    private void changeStat(TaskStats taskStats) {
        if (this.taskStats != taskStats) {
            this.recordTime = System.currentTimeMillis();
            this.taskStats = taskStats;
        }
    }


}
