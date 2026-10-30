package com.fantasykai.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fantasykai.projections.ProjectionDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class SleeperCrosswalkTests {
    private final ObjectMapper json = new ObjectMapper();
    private ProjectionDatabase db;
    private SleeperCrosswalk crosswalk;

    @BeforeEach void setup() {
        db = ProjectionDatabase.get();
        db.reset();
        crosswalk = new SleeperCrosswalk(json, db.jdbc, null, mock(PlatformTransactionManager.class));
    }

    @Test void attachesBothIdsAndKeepsOtherIdsOnRepeat() throws Exception {
        long id = db.player("Josh Palmer", "WR", null);
        db.jdbc.update("UPDATE players SET external_ids = '{\"espn\":\"123\"}'::jsonb WHERE id = ?", id);
        var snapshot = json.readTree("""
                {"77":{"full_name":"Joshua Palmer Jr.","position":"WR","yahoo_id":1234}}
                """);
        for (int i = 0; i < 2; i++) {
            assertThat(crosswalk.attach(snapshot).rowsUpserted()).isEqualTo(1);
            var ids = json.readTree(db.jdbc.queryForObject("SELECT external_ids::text FROM players WHERE id = ?", String.class, id));
            assertThat(ids.path("sleeper").asText()).isEqualTo("77");
            assertThat(ids.path("yahoo").asText()).isEqualTo("1234");
            assertThat(ids.path("espn").asText()).isEqualTo("123");
        }
    }

    @Test void rejectsTwoProviderRecordsForOnePlayer() throws Exception {
        db.player("Josh Palmer", "WR", null);
        var snapshot = json.readTree("""
                {"77":{"full_name":"Josh Palmer","position":"WR"},
                 "78":{"full_name":"Joshua Palmer","position":"WR"}}
                """);
        assertThat(crosswalk.attach(snapshot).rowsUpserted()).isZero();
    }

    @Test void neverOverwritesAnExistingSleeperIdentity() throws Exception {
        long id = db.player("Josh Palmer", "WR", null);
        db.jdbc.update("UPDATE players SET external_ids = '{\"sleeper\":\"old\"}'::jsonb WHERE id = ?", id);
        assertThat(crosswalk.attach(json.readTree("""
                {"new":{"full_name":"Josh Palmer","position":"WR"}}
                """)).rowsUpserted()).isZero();
    }

    @Test void usesAuditedOverridesButDoesNotStealYahooIds() throws Exception {
        long id = db.player("Zonovan Knight", "RB", null);
        long other = db.player("Other Player", "RB", null);
        db.jdbc.update("UPDATE players SET external_ids = '{\"yahoo\":\"55\"}'::jsonb WHERE id = ?", other);
        db.jdbc.update("INSERT INTO player_id_overrides VALUES ('sleeper', '77', ?, 'Known nickname')", id);
        assertThat(crosswalk.attach(json.readTree("""
                {"77":{"full_name":"Bam Knight","position":"RB","yahoo_id":"55"}}
                """)).rowsUpserted()).isEqualTo(1);
        assertThat(db.jdbc.queryForObject("SELECT external_ids->>'yahoo' FROM players WHERE id = ?", String.class, id)).isNull();
    }
}
