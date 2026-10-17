package de.dlrg.vereinsmeisterschaft.repository;

import de.dlrg.vereinsmeisterschaft.model.Models.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@DependsOnDatabaseInitialization
public class RaceRepository {
    private final JdbcTemplate db;
    public RaceRepository(JdbcTemplate db) { this.db = db; }
    public JdbcTemplate db() { return db; }

    public List<Participant> participants() {
        return db.query("SELECT * FROM participant WHERE deleted=FALSE ORDER BY last_name,first_name,id", (r,n) -> participant(r));
    }
    public Optional<Participant> participant(String id) {
        return db.query("SELECT * FROM participant WHERE id=? AND deleted=FALSE", (r,n) -> participant(r),id).stream().findFirst();
    }
    public boolean participantIdUsed(String id) {
        return db.queryForObject("SELECT COUNT(*) FROM participant WHERE id=?",Integer.class,id)>0;
    }
    private Participant participant(ResultSet r) throws SQLException {
        return new Participant(r.getString("id"),r.getString("first_name"),r.getString("last_name"),
                r.getInt("birth_year"),r.getString("category"),r.getString("training_group"));
    }
    public void insert(Participant p) {
        db.update("INSERT INTO participant(id,first_name,last_name,birth_year,category,training_group) VALUES (?,?,?,?,?,?)",p.id(),p.firstName(),p.lastName(),
                p.birthYear(),p.category(),p.trainingGroup());
    }
    public List<Discipline> disciplines() {
        return db.query("""
            SELECT d.name,(SELECT COUNT(*) FROM heat h WHERE LOWER(h.discipline)=d.name_key) AS heat_count
            FROM discipline d ORDER BY d.name_key
            """,(r,n)->new Discipline(r.getString("name"),r.getLong("heat_count")));
    }
    public Optional<String> disciplineName(String name) {
        if(name==null) return Optional.empty();
        return db.query("SELECT name FROM discipline WHERE name_key=?",(r,n)->r.getString("name"),
                name.toLowerCase(Locale.ROOT)).stream().findFirst();
    }
    public void insertDiscipline(String name) {
        db.update("INSERT INTO discipline(name,name_key) VALUES (?,?)",name,name.toLowerCase(Locale.ROOT));
    }
    public List<CompetitionClass> competitionClasses() {
        return db.query("""
            SELECT c.training_group,c.category,(SELECT COUNT(*) FROM participant p
            WHERE p.training_group=c.training_group AND p.category=c.category) AS people
            FROM competition_class c ORDER BY c.training_group,c.category
            """,(r,n)->new CompetitionClass(r.getString("training_group"),r.getString("category"),r.getLong("people")));
    }
    public boolean classExists(String group,String category) {
        return db.queryForObject("SELECT COUNT(*) FROM competition_class WHERE training_group=? AND category=?",Integer.class,group,category)>0;
    }
    public void ensureClass(String group,String category) {
        if(!classExists(group,category)) db.update("INSERT INTO competition_class(training_group,category) VALUES (?,?)",group,category);
    }
    public List<Heat> heats() {
        return db.query("SELECT * FROM heat ORDER BY heat_number DESC", (r,n) -> heat(r));
    }
    public Heat heat(String id) {
        return db.query("SELECT * FROM heat WHERE id=?",(r,n)->heat(r),id).stream().findFirst()
                .orElseThrow(()->new IllegalArgumentException("Lauf nicht gefunden."));
    }
    private Heat heat(ResultSet r) throws SQLException {
        String id=r.getString("id");
        return new Heat(id,r.getLong("heat_number"),r.getString("discipline"),r.getString("label"),
                r.getString("heat_status"),(Double)r.getObject("start_ms"),r.getString("server_boot_id"),
                (Double)r.getObject("start_quality_ms"),r.getString("false_start_of"),lanes(id));
    }
    private List<Lane> lanes(String id) {
        return db.query("SELECT * FROM lane_result WHERE heat_id=? ORDER BY lane_number",(r,n)->new Lane(
                r.getInt("lane_number"),r.getString("participant_id"),r.getString("first_name"),
                r.getString("last_name"),r.getInt("birth_year"),r.getString("category"),r.getString("training_group"),
                r.getString("result_status"),(Double)r.getObject("stop_ms"),(Long)r.getObject("elapsed_ms"),
                (Double)r.getObject("quality_ms"),r.getBoolean("needs_review"),r.getString("note")),id);
    }
    public void audit(String actor,String text) {
        db.update("INSERT INTO audit_log(actor,action_text) VALUES (?,?)",actor,text);
    }
    public List<Map<String,Object>> audit() {
        return db.queryForList("SELECT id,recorded_at,actor,action_text FROM audit_log ORDER BY id DESC LIMIT 200");
    }
}
