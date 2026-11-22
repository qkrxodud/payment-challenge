package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.MismatchResolver;
import com.example.payment.domain.reconciliation.Mismatch;
import org.springframework.context.annotation.Primary;

/**
 * 12장 결정 1·2 비교: 기록만 하고 고치지 않음. 찾은 어긋남을 모두 운영자 확인으로 대사 결과 표에 남기고, 우리 기록이 뒤처진 결제도 맞추지 않는다
 * (결정 1 커밋의 운영 코드와 같다)
 */
@Primary
public class RecordOnlyResolver implements MismatchResolver {

    @Override
    public Mismatch resolve(Mismatch mismatch) {
        return mismatch;
    }
}
