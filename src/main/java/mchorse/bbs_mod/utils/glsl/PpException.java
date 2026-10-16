package mchorse.bbs_mod.utils.glsl;

import java.util.Collections;
import java.util.Set;

/**
 * The preprocessor couldn't be followed exactly. When options are involved they're listed, and
 * keeping them constant is enough to get past the place; without them the source can't be
 * transformed at all.
 */
class PpException extends Exception
{
    final Set<String> symbols;

    PpException(String message, Set<String> symbols)
    {
        super(message);

        this.symbols = symbols == null ? Collections.<String>emptySet() : symbols;
    }
}
