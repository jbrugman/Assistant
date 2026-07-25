package nl.llm.storyteller;

final class ResponseSanitizer {
    String sanitize(String response) {
        String normalized = response == null ? "" : response.trim();
        if (!normalized.contains("\\")) {
            return normalized;
        }

        String current = normalized;
        for (int i = 0; i < 3 && current.contains("\\"); i++) {
            String next = unescapeVisibleJsonEscapes(current);
            if (next.equals(current)) {
                return current;
            }
            current = next;
        }
        return current;
    }

    private String unescapeVisibleJsonEscapes(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean changed = false;

        int index = 0;
        while (index < value.length()) {
            char ch = value.charAt(index);
            int consumed = 1;

            if (ch == '\\' && index + 1 < value.length()) {
                char next = value.charAt(index + 1);
                Character replacement = switch (next) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> null;
                };

                if (replacement != null) {
                    result.append(replacement);
                    consumed = 2;
                    changed = true;
                } else {
                    result.append(ch);
                }
            } else {
                result.append(ch);
            }

            index += consumed;
        }

        return changed ? result.toString().trim() : value;
    }
}
