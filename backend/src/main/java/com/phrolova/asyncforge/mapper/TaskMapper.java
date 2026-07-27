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
            SET status = #{toStatus}, updated_at = NOW()
            WHERE id = #{taskId}
              AND status IN ('PENDING', 'FAILED')
            """)
    int claimForExecution(@Param("taskId") Long taskId,
                          @Param("toStatus") String toStatus);
}
