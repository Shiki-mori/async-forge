package com.phrolova.asyncforge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phrolova.asyncforge.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    @Update("""
            UPDATE task
            SET status = 'RUNNING', updated_at = NOW()
            WHERE id = #{taskId}
              AND (
                  (status = 'PENDING' AND #{retryCount,jdbcType=INTEGER} IS NULL)
                  OR (status = 'FAILED' AND retry_count = #{retryCount,jdbcType=INTEGER})
              )
            """)
    int claimForExecution(@Param("taskId") Long taskId,
                          @Param("retryCount") Integer retryCount);
}
