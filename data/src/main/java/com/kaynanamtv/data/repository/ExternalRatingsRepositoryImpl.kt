package com.kaynanamtv.data.repository

import com.kaynanamtv.domain.model.ExternalRatings
import com.kaynanamtv.domain.model.ExternalRatingsLookup
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.repository.ExternalRatingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalRatingsRepositoryImpl @Inject constructor() : ExternalRatingsRepository {

    override suspend fun getRatings(lookup: ExternalRatingsLookup): Result<ExternalRatings> {
        return Result.success(ExternalRatings.unavailable())
    }
}