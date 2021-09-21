package com.checkmarx.intellij.tool.window.actions.group.by;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.tool.window.results.tree.GroupBy;

@SuppressWarnings("ComponentNotRegistered")
public class GroupByQueryNameAction extends GroupByBaseAction {

    public GroupByQueryNameAction() {
        super(Bundle.messagePointer(Resource.QUERY_NAME_ACTION));
    }

    @Override
    protected GroupBy getGroupBy() {
        return GroupBy.QUERY_NAME;
    }
}
