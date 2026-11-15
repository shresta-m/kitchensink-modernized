package com.kitchensink.dto.imports;



import com.kitchensink.model.Member;

/** A validated, in-file-unique row waiting to be written. */
public record ImportCandidate(int rowNumber, Member member) {}
