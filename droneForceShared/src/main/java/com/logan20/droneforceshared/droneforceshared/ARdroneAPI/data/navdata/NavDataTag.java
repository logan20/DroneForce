package com.logan20.droneforceshared.droneforceshared.ARdroneAPI.data.navdata;

public enum NavDataTag
{
    NAVDATA_DEMO_TAG(0), NAVDATA_TIME_TAG(1), NAVDATA_RAW_MEASURES_TAG(2), NAVDATA_PHYS_MEASURES_TAG(3), NAVDATA_GYROS_OFFSETS_TAG(
            4), NAVDATA_EULER_ANGLES_TAG(5), NAVDATA_REFERENCES_TAG(6), NAVDATA_TRIMS_TAG(7), NAVDATA_RC_REFERENCES_TAG(
            8), NAVDATA_PWM_TAG(9), NAVDATA_ALTITUDE_TAG(10), NAVDATA_VISION_RAW_TAG(11), NAVDATA_VISION_OF_TAG(12), NAVDATA_VISION_TAG(
            13), NAVDATA_VISION_PERF_TAG(14), NAVDATA_TRACKERS_SEND_TAG(15), NAVDATA_VISION_DETECT_TAG(16), NAVDATA_WATCHDOG_TAG(
            17), NAVDATA_ADC_DATA_FRAME_TAG(18), NAVDATA_VIDEO_STREAM_TAG(19), NAVDATA_CKS_TAG(0xFFFF);

    private int value;

    private NavDataTag(int value)
    {
        this.value = value;
    }

    public int getValue()
    {
        return value;
    }
}