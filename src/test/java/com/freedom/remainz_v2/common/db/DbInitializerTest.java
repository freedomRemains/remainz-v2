package com.freedom.remainz_v2.common.db;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DbInitializerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DbInitializationService dbInitializationService;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private DbInitializer dbInitializer;

    @Test
    void TBL_DEFテーブルが存在しない場合は初期化処理が実行されること() {

        when(jdbcTemplate.queryForList(anyString(), any(Class.class), anyString())).thenReturn(List.of());

        dbInitializer.run(applicationArguments);

        verify(dbInitializationService).initializeDatabase();
    }

    @Test
    void TBL_DEFテーブルが既に存在する場合は初期化処理が実行されないこと() {

        when(jdbcTemplate.queryForList(anyString(), any(Class.class), anyString())).thenReturn(List.of("TBL_DEF"));

        dbInitializer.run(applicationArguments);

        verify(dbInitializationService, never()).initializeDatabase();
    }
}
