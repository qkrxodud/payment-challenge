package com.example.payment.application.payment;

import com.example.payment.domain.reconciliation.Mismatch;

/**
 * 12장 결정 2: 대사가 찾은 어긋남을 대사 안에서 어떻게 처리할지. 처리한 어긋남(맞췄으면 맞춤으로 바뀐)을 돌려주고, 그것이 대사 결과 표에 남는다
 */
public interface MismatchResolver {

    Mismatch resolve(Mismatch mismatch);
}
