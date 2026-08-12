package com.phrolova.asyncforge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phrolova.asyncforge.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /*
    DB层幂等
    仅当任务状态为PENDING或FAILED时，才更新状态为RUNNING
    更改成功，返回1，表示获取到任务执行权
    更改失败，返回0，表示任务状态不符合条件或已被其他消费者获取
    */
    @Update("""
            UPDATE task
            SET status = #{toStatus}, updated_at = NOW()
            WHERE id = #{taskId}
              AND status IN ('PENDING', 'FAILED')
            """)
    int claimForExecution(@Param("taskId") Long taskId,
                          @Param("toStatus") String toStatus);
}
