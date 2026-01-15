# DESIGN

There were still problems with either the last state or the last links in some cases, for example when the last change on a defect consists in to change its type into a task and mark it as resolve with duplicate. Adding a duplicate link changes the state, but the type has changed, too. After some testing with different solutions, and checking on the result with rtc.pa.model.utilities.Compare, this order works, which adds an extra step at the end, to ensure the last state is the expected one:

1. Create categories
2. Create development lines and iterations
3. For each work item, create a minimal work item version
    - and collect the matching between source IDs and target IDs (needed for step 5)
4. For each work item again, update by adding each version from the history (+ change automatic links in descriptions and comments):
    - builtin attributes
    - custom attributes
    - comments
    - tags
    - subscribers
    - change state if needed
    - change WI type if needed
5. For each work item again, update with:
    - links
    - attachments
    - approvals
6. For each work item for the last time, update with the last version, especially its state.

