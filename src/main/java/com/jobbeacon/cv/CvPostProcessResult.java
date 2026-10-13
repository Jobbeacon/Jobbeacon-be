package com.jobbeacon.cv;

import com.jobbeacon.dto.cv.CvParsedPayload;

import java.util.List;

/**
 * Output of {@link CvPostProcessor}.
 *
 * @param payload                final merged payload (skills not yet resolved to DB rows)
 * @param hallucinatedSkillCount model skills dropped because they do not appear in the raw text
 * @param droppedFields          paths of model values removed by the cross-check, e.g.
 *                               {@code contact.email}, {@code contact.links[2]}, {@code skills[5]}
 *                               (indexes refer to the model payload); never contains CV values
 */
public record CvPostProcessResult(
    CvParsedPayload payload,
    int hallucinatedSkillCount,
    List<String> droppedFields
) {
}
