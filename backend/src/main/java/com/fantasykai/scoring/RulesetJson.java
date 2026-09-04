package com.fantasykai.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Reads and writes the {@code scoring_profiles.rules} JSONB document.
 *
 * <p>Hand-rolled against the tree model rather than annotation binding, because
 * the requirement here is not "parse what you recognise" but "reject anything
 * you don't". Jackson quietly ignoring an unknown key is exactly the failure
 * this has to avoid: a typo'd stat name would then score as zero forever
 * instead of telling the user their ruleset is wrong.
 */
@Component
public class RulesetJson {

    /** Positions an override may name. K and DST are accepted so v2 profiles round-trip. */
    private static final Set<String> POSITIONS = Set.of("QB", "RB", "WR", "TE", "K", "DST");

    private static final Set<String> TOP_LEVEL =
            Set.of("version", "base", "position_overrides", "bonuses");
    private static final Set<String> BONUS_FIELDS = Set.of("stat", "gte", "points");

    private final ObjectMapper mapper;

    public RulesetJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Ruleset read(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new InvalidRulesetException("ruleset is not valid JSON: " + e.getOriginalMessage());
        }
        if (root == null || !root.isObject()) {
            throw new InvalidRulesetException("ruleset must be a JSON object");
        }
        rejectUnknown(root, TOP_LEVEL, "ruleset");

        if (!root.path("version").isInt()) {
            throw new InvalidRulesetException("ruleset needs an integer \"version\"");
        }
        int version = root.get("version").asInt();

        Map<StatKey, Double> base = rates(root.path("base"), "base");

        Map<String, Map<StatKey, Double>> overrides = new HashMap<>();
        JsonNode overrideNode = root.path("position_overrides");
        if (!overrideNode.isMissingNode() && !overrideNode.isNull()) {
            if (!overrideNode.isObject()) {
                throw new InvalidRulesetException("\"position_overrides\" must be an object");
            }
            for (Iterator<String> it = overrideNode.fieldNames(); it.hasNext();) {
                String position = it.next();
                if (!POSITIONS.contains(position)) {
                    throw new InvalidRulesetException("unknown position \"" + position
                            + "\" in position_overrides; expected one of " + POSITIONS);
                }
                overrides.put(position,
                        rates(overrideNode.get(position), "position_overrides." + position));
            }
        }

        return new Ruleset(version, base, overrides, bonuses(root.path("bonuses")));
    }

    public String write(Ruleset ruleset) {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", ruleset.version());

        ObjectNode base = root.putObject("base");
        ruleset.base().forEach((stat, rate) -> base.put(stat.json(), rate));

        if (!ruleset.positionOverrides().isEmpty()) {
            ObjectNode overrides = root.putObject("position_overrides");
            ruleset.positionOverrides().forEach((position, rates) -> {
                ObjectNode node = overrides.putObject(position);
                rates.forEach((stat, rate) -> node.put(stat.json(), rate));
            });
        }
        if (!ruleset.bonuses().isEmpty()) {
            ArrayNode bonuses = root.putArray("bonuses");
            ruleset.bonuses().forEach(bonus -> {
                ObjectNode node = bonuses.addObject();
                node.put("stat", bonus.stat().json());
                node.put("gte", bonus.gte());
                node.put("points", bonus.points());
            });
        }
        return root.toString();
    }

    private Map<StatKey, Double> rates(JsonNode node, String where) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new InvalidRulesetException("\"" + where + "\" must be an object");
        }
        Map<StatKey, Double> rates = new EnumMap<>(StatKey.class);
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> field = it.next();
            StatKey stat = StatKey.fromJson(field.getKey())
                    .orElseThrow(() -> new InvalidRulesetException(
                            "unknown stat \"" + field.getKey() + "\" in " + where));
            if (!field.getValue().isNumber()) {
                throw new InvalidRulesetException(
                        where + "." + field.getKey() + " must be a number");
            }
            rates.put(stat, field.getValue().asDouble());
        }
        return rates;
    }

    private List<Bonus> bonuses(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new InvalidRulesetException("\"bonuses\" must be an array");
        }
        List<Bonus> bonuses = new ArrayList<>();
        for (JsonNode entry : node) {
            if (!entry.isObject()) {
                throw new InvalidRulesetException("each bonus must be an object");
            }
            rejectUnknown(entry, BONUS_FIELDS, "bonus");
            StatKey stat = StatKey.fromJson(entry.path("stat").asText(""))
                    .orElseThrow(() -> new InvalidRulesetException(
                            "unknown stat \"" + entry.path("stat").asText("") + "\" in a bonus"));
            if (!entry.path("gte").isNumber() || !entry.path("points").isNumber()) {
                throw new InvalidRulesetException("a bonus needs numeric \"gte\" and \"points\"");
            }
            bonuses.add(new Bonus(stat, entry.get("gte").asInt(), entry.get("points").asDouble()));
        }
        return bonuses;
    }

    private static void rejectUnknown(JsonNode node, Set<String> allowed, String where) {
        for (Iterator<String> it = node.fieldNames(); it.hasNext();) {
            String field = it.next();
            if (!allowed.contains(field)) {
                throw new InvalidRulesetException(
                        "unknown field \"" + field + "\" in " + where + "; expected one of " + allowed);
            }
        }
    }
}
