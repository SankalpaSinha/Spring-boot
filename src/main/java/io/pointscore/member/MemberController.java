package io.pointscore.member;

import io.pointscore.auth.AuthService;
import io.pointscore.member.MemberDtos.CreateMemberRequest;
import io.pointscore.member.MemberDtos.MemberResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;
    private final AuthService authService;

    public MemberController(MemberService memberService, AuthService authService) {
        this.memberService = memberService;
        this.authService = authService;
    }

    /** Sign-up: enrols the member and creates their login together. Public. */
    @PostMapping
    public ResponseEntity<MemberResponse> enrol(@Valid @RequestBody CreateMemberRequest request,
                                                UriComponentsBuilder uriBuilder) {
        Member member = authService.signUp(request.name(), request.email(), request.password());

        URI location = uriBuilder.path("/api/members/{id}").buildAndExpand(member.getId()).toUri();
        return ResponseEntity.created(location).body(MemberResponse.from(member));
    }

    /**
     * Transactional because MemberResponse touches the lazily-loaded tier, and
     * open-in-view is off -- without an open session that read would fail.
     */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public MemberResponse get(@PathVariable Long id) {
        return MemberResponse.from(memberService.require(id));
    }
}
