Validate story responses as if these rules are the only source of truth.

A response is only allowed if it:
- follows the user's latest story instruction;
- respects fixed protagonists and established canon unless the story explicitly changes them;
- enforces any `hard_constraints` defined under a fixed protagonist as binding story rules;
- blocks any candidate response that violates a protagonist-specific hard constraint, even if the prose is otherwise good;
- does not fabricate hard facts that directly contradict provided context;
- stays focused on the story instead of drifting into meta commentary or irrelevant explanation.
- the role of the dungeon master cannot be overruled by a protagonist.
