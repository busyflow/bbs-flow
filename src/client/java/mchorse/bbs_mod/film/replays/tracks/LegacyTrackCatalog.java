package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;

/** Existing independent curves remain editable; new rigs never enumerate these legacy paths. */
final class LegacyTrackCatalog
{
    static void append(Form root, FormProperties properties, List<TrackDescriptor> tracks)
    {
        if (properties == null) return;
        properties.tracks.forEach((id, channel) ->
        {
            if (channel.isEmpty() || tracks.stream().anyMatch(track -> track.id().equals(id))) return;
            boolean legacy = id.kind().isSolver() || (id.kind() == TrackKind.PROPERTY && id.subject().startsWith("splines/"));
            if (!legacy) return;
            Form owner = FormUtils.getForm(root, id.formPath());
            if (owner == null) return;
            tracks.add(new TrackDescriptor(id, channel, owner, TrackStyle.label(id), Icons.CURVES, Colors.GRAY,
                id.kind() == TrackKind.PROPERTY ? FormUtils.getProperty(root, id.toKey()) : null));
        });
    }
}
