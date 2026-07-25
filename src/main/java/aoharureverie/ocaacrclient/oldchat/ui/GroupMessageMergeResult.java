package aoharureverie.ocaacrclient.oldchat.ui;

class GroupMessageMergeResult {
    final int currentOffset;
    final boolean hasMore;
    final boolean gapReset;

    GroupMessageMergeResult(int currentOffset, boolean hasMore, boolean gapReset) {
        this.currentOffset = currentOffset;
        this.hasMore = hasMore;
        this.gapReset = gapReset;
    }
}
