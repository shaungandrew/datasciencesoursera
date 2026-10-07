package com.aaii.yctamember

data class Member(
    val name: String = "",
    val memberId: String = "",
    val driverLicense: String = "",
    val joinedDate: String = "",
    val vehicleNo: String = "",
    val cityNo: String = "",
    val district: String = "",
    val photoUrls: List<String> = emptyList(),
    val maskedPhone: String = "",
    val maskedNrc: String = "",
    val maskedAddress: String = "",
    val profileUrl: String
)

data class MemberSummary(
    val title: String,
    val subtitle: String,
    val profileUrl: String
)

sealed class SearchOutcome {
    data class Direct(val member: Member) : SearchOutcome()
    data class Results(val members: List<MemberSummary>) : SearchOutcome()
    data class Failure(val message: String) : SearchOutcome()
}
