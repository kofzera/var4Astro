package cz.muni.var4astro.dao;

import cz.muni.var4astro.dto.ObjectFluxCount;
import cz.muni.var4astro.dto.SpaceObject;
import cz.muni.var4astro.mappers.ObjectFluxCountRowMapper;
import cz.muni.var4astro.setters.SpaceObjectPreparedStatementSetter;
import cz.muni.var4astro.mappers.SpaceObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;

/**
 * The Data Access Object for the space_object entity.
 */
@Repository
@Transactional
public class SpaceObjectDaoImpl implements SpaceObjectDao {


    private JdbcTemplate jdbcTemplate;

    @Autowired
    public void init(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * Method to query objects based on the parameters given in the search form
     */
    @Override
    public List<ObjectFluxCount> queryObjects(String RA, String dec, String radius,
                                              String name, String minMag,
                                              String maxMag, String catalog, String objectId) {
        StringBuilder query = new StringBuilder("SELECT space_object.id AS obj_id, " +
                "name, catalog, catalog_id, catalog_rec, catalog_dec, " +
                "catalog_mag, count(flux.id) AS flux_count" +
                " FROM space_object LEFT OUTER JOIN flux ON object_id=space_object.id");
        boolean appendAnd = false;
        if (!RA.isEmpty()) {
            query.append(" WHERE");
            query.append(" earth_box(ll_to_earth(?, ?), ?) @> space_object.coordinates");
            appendAnd = true;
        }
        if (!name.isEmpty()) {
            if (appendAnd) {
                query.append(" AND");
            } else {
                query.append(" WHERE");
            }
            query.append(" name LIKE ?");
            appendAnd = true;
        }
        if (!(minMag.equals("0") && (maxMag.equals("15")))) {
            if (appendAnd) {
                query.append(" AND");
            } else {
                query.append(" WHERE");
            }
            query.append(" catalog_mag BETWEEN ? AND ?");
            appendAnd = true;
        }

        if (!catalog.equals("All catalogues")) {
            if (appendAnd) {
                query.append(" AND");
            } else {
                query.append(" WHERE");
            }
            query.append(" catalog LIKE ?");
            appendAnd = true;
        }

        if (!objectId.isEmpty()) {
            if (appendAnd) {
                query.append(" AND");
            } else {
                query.append(" WHERE");
            }
            query.append(" catalog_id LIKE ?");
        }
        query.append(" GROUP BY space_object.id, name, catalog, catalog_id," +
                " catalog_rec, catalog_dec, catalog_mag LIMIT 100");
        String finishedQuery = query.toString();
        return jdbcTemplate.query(finishedQuery, new SpaceObjectPreparedStatementSetter(
                RA, dec, radius, name, minMag, maxMag, catalog, objectId), new ObjectFluxCountRowMapper());
    }

    @Override
    public long saveObject(String catalogId, String name, String catalog, String strDec, String strRec,
                           Float catalogDec, Float catalogRec, Float catalogMag) {
        assert jdbcTemplate != null;
        List<Long> existingIds = jdbcTemplate.queryForList("SELECT id FROM space_object " +
                "WHERE catalog_id=?", Long.class, catalogId);
        if (existingIds.size() != 0) {
            return existingIds.get(0);
        }
        String insertQuery = "INSERT INTO space_object " +
                "(id, name, catalog, catalog_id, catalog_rec, catalog_dec, coordinates, catalog_mag)" +
                "VALUES (nextval('space_object_id_seq'), ?, ?, ?, ?, ?, ll_to_earth(?, ?), ?)";
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertQuery, new String[]{"id"});
            ps.setString(1, name);
            ps.setString(2, catalog);
            ps.setString(3, catalogId);
            ps.setString(4, strRec);
            ps.setString(5, strDec);
            ps.setFloat(6, catalogDec);
            ps.setFloat(7, catalogRec);
            ps.setFloat(8, catalogMag);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    @Override
    public List<String> getAvailableCatalogues() {
        return jdbcTemplate
                .queryForList("SELECT DISTINCT catalog FROM space_object", String.class);
    }

    @Override
    public SpaceObject getSpaceObjectById(Long id) {
        String query = "SELECT catalog, catalog_id, catalog_rec, catalog_dec, catalog_mag " +
                "FROM space_object WHERE id=?";
        return jdbcTemplate.queryForObject(query, new SpaceObjectMapper(), id);
    }

    @Override
    public Long getNumberOfObjectsEstimate() {
        return jdbcTemplate
                .queryForObject("SELECT reltuples FROM pg_class WHERE relname = 'space_object'", Long.class);
    }
}