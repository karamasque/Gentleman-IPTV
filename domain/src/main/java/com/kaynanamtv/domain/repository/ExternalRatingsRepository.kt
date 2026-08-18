package com.kaynanamtv.domain.repository

import com.kaynanamtv.domain.model.ExternalRatings
import com.kaynanamtv.domain.model.ExternalRatingsLookup
import com.kaynanamtv.domain.model.Result

interface ExternalRatingsRepository {
    suspend fun getRatings(lookup: ExternalRatingsLookup): Result<ExternalRatings>
}