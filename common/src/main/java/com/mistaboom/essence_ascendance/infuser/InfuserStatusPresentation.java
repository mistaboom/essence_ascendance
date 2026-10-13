package com.mistaboom.essence_ascendance.infuser;

/** Shared status vocabulary for the machine menu and membership/settings interface. */
public final class InfuserStatusPresentation {
    private InfuserStatusPresentation() {}
    public static String key(int status) {
        String key = switch (status) {
            case EssenceInfuserBlockEntity.STATUS_STOPPED -> "stopped";
            case EssenceInfuserBlockEntity.STATUS_UNLINKED -> "unlinked";
            case EssenceInfuserBlockEntity.STATUS_INVALID_SELECTION -> "invalid_selection";
            case EssenceInfuserBlockEntity.STATUS_INSUFFICIENT_SOURCE -> "insufficient_source";
            case EssenceInfuserBlockEntity.STATUS_OUTPUT_BLOCKED -> "output_blocked";
            case EssenceInfuserBlockEntity.STATUS_PROCESSING -> "processing";
            case EssenceInfuserBlockEntity.STATUS_INVALID_INPUT -> "invalid_input";
            case EssenceInfuserBlockEntity.STATUS_JOB_CONTEXT_REQUIRED -> "job_context_required";
            case EssenceInfuserBlockEntity.STATUS_RECIPIENT_REQUIRED -> "recipient_required";
            case EssenceInfuserBlockEntity.STATUS_WAITING_BANDWIDTH -> "waiting_bandwidth";
            case EssenceInfuserBlockEntity.STATUS_FOCUS_TIER_REQUIRED -> "focus_tier_required";
            case EssenceInfuserBlockEntity.STATUS_FOCUS_MALFORMED -> "focus_malformed";
            case EssenceInfuserBlockEntity.STATUS_NEEDS_FOCUS -> "needs_focus";
            case EssenceInfuserBlockEntity.STATUS_COMPONENT_REQUIRED -> "component_required";
            case EssenceInfuserBlockEntity.STATUS_REPAIR_MATERIAL_REQUIRED -> "repair_material_required";
            default -> "waiting_input";
        };
        return "gui.essence_ascendance.infuser.status." + key;
    }
}
