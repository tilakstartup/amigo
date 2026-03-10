# Action Group Name Fix

## Issue
The `GoalManagement` action group was created with PascalCase name instead of snake_case (`goal_management`), causing validation errors when the client tried to send return control results back to Bedrock.

## Root Cause
The action group was likely created manually or with an earlier version of the CDK code that didn't properly set the action group name.

## Fix Applied (2026-03-10)
Manually updated the action group name using AWS CLI:

```bash
# Updated the action group name from "GoalManagement" to "goal_management"
aws bedrock-agent update-agent-action-group \
  --agent-id FDONOFHDHR \
  --agent-version DRAFT \
  --action-group-id F0RRZBC2CR \
  --action-group-name goal_management \
  --action-group-executor '{"customControl": "RETURN_CONTROL"}' \
  --action-group-state ENABLED \
  --api-schema file:///tmp/goal-schema-wrapped.json

# Prepared the agent to apply changes
aws bedrock-agent prepare-agent --agent-id FDONOFHDHR
```

## Verification
```bash
# Verify all action groups have snake_case names
aws bedrock-agent list-agent-action-groups \
  --agent-id FDONOFHDHR \
  --agent-version DRAFT \
  --query 'actionGroupSummaries[*].[actionGroupName,actionGroupId]' \
  --output table
```

Expected output:
```
---------------------------------------
|        ListAgentActionGroups        |
+----------------------+--------------+
|  data_operations     |  OZXOLVLYCF  |
|  goal_management     |  F0RRZBC2CR  |
|  health_calculations |  JMC2WCIQOR  |
+----------------------+--------------+
```

## CDK Code Status
The CDK code in `infrastructure/lib/stacks/bedrock-agent/stack.ts` is correct and uses snake_case names:
- `data_operations` (line 88)
- `health_calculations` (line 95)
- `goal_management` (line 104)

## Prevention
To prevent this issue in the future:
1. Always use snake_case for action group names in AWS Bedrock
2. Ensure the CDK code matches the client-side action group registry names
3. After any manual changes, run `cdk deploy` to sync CloudFormation state
4. Verify action group names after deployment using the AWS CLI command above

## Related Files
- Infrastructure: `infrastructure/lib/stacks/bedrock-agent/stack.ts`
- Client Registry: `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/actions/ActionGroupRegistry.kt`
- Action Groups:
  - `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/actions/DataOperationsActionGroup.kt` (name: `data_operations`)
  - `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/actions/HealthCalculationsActionGroup.kt` (name: `health_calculations`)
  - `mobile/shared/src/commonMain/kotlin/com/amigo/shared/ai/actions/GoalManagementActionGroup.kt` (name: `goal_management`)
